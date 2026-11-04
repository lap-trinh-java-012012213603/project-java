package com.analoghub.platform.aiassistant.client;

import com.analoghub.platform.aiassistant.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiModelAdapter implements LlmClient, EmbeddingClient, VisionLlmClient {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public ModelProvider provider() {
        return ModelProvider.OPENAI;
    }

    @Override
    public String modelName() {
        String configured = aiProperties.getOpenai().getEmbeddingModel();
        return StringUtils.hasText(configured) ? configured : "text-embedding-3-small";
    }

    @Override
    public int configuredDimensions() {
        int dims = aiProperties.getOpenai().getEmbeddingDimensions();
        return dims > 0 ? dims : REQUIRED_DIMENSIONS;
    }

    @Override
    public LlmResponse generate(LlmPromptRequest request) {
        long startNanos = System.nanoTime();
        String model = resolveChatModel(request);

        if (isApiKeyConfigured()) {
            try {
                RestClient restClient = buildRestClient();
                List<Map<String, String>> messages = new ArrayList<>();
                if (StringUtils.hasText(request.systemInstruction())) {
                    messages.add(Map.of("role", "system", "content", request.systemInstruction()));
                }
                if (request.history() != null) {
                    for (LlmMessage msg : request.history()) {
                        String role = "model".equalsIgnoreCase(msg.role()) ? "assistant" : msg.role();
                        messages.add(Map.of("role", role, "content", msg.content()));
                    }
                }
                if (StringUtils.hasText(request.userPrompt())) {
                    messages.add(Map.of("role", "user", "content", request.userPrompt()));
                }

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("model", model);
                payload.put("messages", messages);
                payload.put("temperature", request.temperature() != null
                        ? request.temperature()
                        : aiProperties.getOpenai().getTemperature());
                payload.put("max_tokens", request.maxOutputTokens() != null
                        ? request.maxOutputTokens()
                        : aiProperties.getOpenai().getMaxOutputTokens());

                if (StringUtils.hasText(request.responseSchemaJson())) {
                    payload.put("response_format", Map.of("type", "json_object"));
                }

                JsonNode responseNode = restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(JsonNode.class);

                if (responseNode != null) {
                    String content = responseNode.path("choices").path(0).path("message").path("content").asText("");
                    String finishReason = responseNode.path("choices").path(0).path("finish_reason").asText("stop");
                    int promptTokens = responseNode.path("usage").path("prompt_tokens").asInt(0);
                    int completionTokens = responseNode.path("usage").path("completion_tokens").asInt(0);
                    long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);

                    return LlmResponse.builder()
                            .textContent(content)
                            .tokenUsage(TokenUsage.of(promptTokens, completionTokens))
                            .finishReason(finishReason)
                            .latencyMs(latencyMs)
                            .providerUsed(ModelProvider.OPENAI)
                            .modelNameUsed(model)
                            .build();
                }
            } catch (Exception ex) {
                log.warn("OpenAI chat completion call failed (model={}): {}", model, ex.getMessage());
                throw new IllegalStateException("OpenAI generation failed: " + ex.getMessage(), ex);
            }
        }

        String fallbackText = request != null && StringUtils.hasText(request.responseSchemaJson())
                ? "{}"
                : "Phản hồi kỹ thuật từ OpenAI Assistant cho yêu cầu: "
                  + (request != null && request.userPrompt() != null ? request.userPrompt() : "");
        TokenUsage usage = GeminiModelAdapter.estimateTokenUsage(
                request != null ? request.userPrompt() : "",
                fallbackText
        );
        long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);

        return LlmResponse.builder()
                .textContent(fallbackText)
                .tokenUsage(usage)
                .finishReason("stop")
                .latencyMs(latencyMs)
                .providerUsed(ModelProvider.OPENAI)
                .modelNameUsed(model)
                .build();
    }

    @Override
    public <T> StructuredLlmResponse<T> generateStructured(LlmPromptRequest request, Class<T> responseClass) {
        LlmPromptRequest jsonRequest = LlmPromptRequest.builder()
                .systemInstruction(request.systemInstruction())
                .history(request.history())
                .userPrompt(request.userPrompt())
                .temperature(request.temperature())
                .maxOutputTokens(request.maxOutputTokens())
                .responseSchemaJson(StringUtils.hasText(request.responseSchemaJson())
                        ? request.responseSchemaJson()
                        : "{}")
                .promptKey(request.promptKey())
                .promptVersion(request.promptVersion())
                .build();

        LlmResponse rawResponse = generate(jsonRequest);
        String cleanedJson = GeminiModelAdapter.stripMarkdownJsonFence(rawResponse.textContent());
        try {
            T parsed = objectMapper.readValue(cleanedJson, responseClass);
            return StructuredLlmResponse.<T>builder()
                    .data(parsed)
                    .rawJsonText(cleanedJson)
                    .tokenUsage(rawResponse.tokenUsage())
                    .latencyMs(rawResponse.latencyMs())
                    .providerUsed(rawResponse.providerUsed())
                    .modelNameUsed(rawResponse.modelNameUsed())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to deserialize OpenAI structured response into " + responseClass.getSimpleName(), ex);
        }
    }

    @Override
    public float[] embed(String text, EmbeddingTaskType taskType) {
        int targetDims = configuredDimensions();
        if (isApiKeyConfigured()) {
            try {
                RestClient restClient = buildRestClient();
                Map<String, Object> payload = Map.of(
                        "model", modelName(),
                        "input", text != null ? text : "",
                        "dimensions", targetDims
                );
                JsonNode responseNode = restClient.post()
                        .uri("/embeddings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(JsonNode.class);

                JsonNode embeddingNode = responseNode != null
                        ? responseNode.path("data").path(0).path("embedding")
                        : null;
                if (embeddingNode != null && embeddingNode.isArray() && !embeddingNode.isEmpty()) {
                    float[] raw = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        raw[i] = (float) embeddingNode.get(i).asDouble(0.0);
                    }
                    return EmbeddingClient.normalizeL2(raw, targetDims);
                }
            } catch (Exception ex) {
                log.warn("OpenAI embeddings call failed, using deterministic L2-normalized vector: {}", ex.getMessage());
            }
        }
        return EmbeddingClient.deterministicEmbedding(text, taskType, targetDims);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, EmbeddingTaskType taskType) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text, taskType));
        }
        return results;
    }

    @Override
    public VisionDiagnosticResult analyzeScanFrame(
            byte[] compressedWebpImageBytes,
            String mimeType,
            ClassicalCvMetrics cvMetrics,
            String specificPrompt
    ) {
        if (isApiKeyConfigured() && compressedWebpImageBytes != null && compressedWebpImageBytes.length > 0) {
            try {
                String effectiveMime = StringUtils.hasText(mimeType) ? mimeType : "image/webp";
                String base64Image = Base64.getEncoder().encodeToString(compressedWebpImageBytes);
                String dataUri = "data:" + effectiveMime + ";base64," + base64Image;
                String visionModel = StringUtils.hasText(aiProperties.getOpenai().getVisionModel())
                        ? aiProperties.getOpenai().getVisionModel()
                        : "gpt-4o-mini";

                Map<String, Object> userMessage = Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", specificPrompt != null ? specificPrompt : "Analyze scan quality."),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
                        )
                );
                Map<String, Object> payload = Map.of(
                        "model", visionModel,
                        "messages", List.of(userMessage),
                        "response_format", Map.of("type", "json_object")
                );

                JsonNode responseNode = buildRestClient().post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(JsonNode.class);

                String content = responseNode != null
                        ? responseNode.path("choices").path(0).path("message").path("content").asText("")
                        : "";
                if (StringUtils.hasText(content)) {
                    return objectMapper.readValue(
                            GeminiModelAdapter.stripMarkdownJsonFence(content),
                            VisionDiagnosticResult.class
                    );
                }
            } catch (Exception ex) {
                log.warn("OpenAI vision analysis failed, falling back to heuristic CV evaluation: {}", ex.getMessage());
            }
        }
        return GeminiModelAdapter.buildHeuristicVisionResult(cvMetrics);
    }

    private boolean isApiKeyConfigured() {
        String apiKey = aiProperties.getOpenai().getApiKey();
        return StringUtils.hasText(apiKey)
                && !apiKey.startsWith("your-")
                && !apiKey.startsWith("${")
                && !"dummy".equalsIgnoreCase(apiKey)
                && !"test".equalsIgnoreCase(apiKey);
    }

    private RestClient buildRestClient() {
        String baseUrl = StringUtils.hasText(aiProperties.getOpenai().getBaseUrl())
                ? aiProperties.getOpenai().getBaseUrl()
                : "https://api.openai.com/v1";
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + aiProperties.getOpenai().getApiKey())
                .build();
    }

    private String resolveChatModel(LlmPromptRequest request) {
        if (request != null && StringUtils.hasText(request.promptKey())
                && request.promptKey().toUpperCase().contains("COMPLEX")) {
            String pro = aiProperties.getOpenai().getProModel();
            if (StringUtils.hasText(pro)) {
                return pro;
            }
        }
        String chat = aiProperties.getOpenai().getChatModel();
        return StringUtils.hasText(chat) ? chat : "gpt-4o-mini";
    }
}

