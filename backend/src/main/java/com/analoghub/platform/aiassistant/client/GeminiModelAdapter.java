package com.analoghub.platform.aiassistant.client;

import com.analoghub.platform.aiassistant.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiModelAdapter implements LlmClient, EmbeddingClient, VisionLlmClient {

    private final ObjectProvider<Client> geminiClientProvider;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public ModelProvider provider() {
        return ModelProvider.GEMINI;
    }

    @Override
    public String modelName() {
        String configured = aiProperties.getGemini().getEmbeddingModel();
        return StringUtils.hasText(configured) ? configured : "text-embedding-004";
    }

    @Override
    public int configuredDimensions() {
        int dims = aiProperties.getGemini().getEmbeddingDimensions();
        return dims > 0 ? dims : REQUIRED_DIMENSIONS;
    }

    @Override
    public LlmResponse generate(LlmPromptRequest request) {
        long startNanos = System.nanoTime();
        String model = resolveChatModel(request);

        Client client = getActiveClientIfConfigured();
        if (client != null) {
            try {
                GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();
                float temp = request.temperature() != null
                        ? request.temperature().floatValue()
                        : (float) aiProperties.getGemini().getTemperature();
                int maxTokens = request.maxOutputTokens() != null
                        ? request.maxOutputTokens()
                        : aiProperties.getGemini().getMaxOutputTokens();

                configBuilder.temperature(temp).maxOutputTokens(maxTokens);

                if (StringUtils.hasText(request.systemInstruction())) {
                    configBuilder.systemInstruction(Content.fromParts(Part.fromText(request.systemInstruction())));
                }
                if (StringUtils.hasText(request.responseSchemaJson())) {
                    configBuilder.responseMimeType("application/json");
                }

                String promptText = buildCombinedPromptText(request);
                GenerateContentResponse response = client.models.generateContent(
                        model,
                        promptText,
                        configBuilder.build()
                );

                String textContent = response.text() != null ? response.text() : "";
                TokenUsage usage = extractTokenUsage(response, promptText, textContent);
                long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);

                return LlmResponse.builder()
                        .textContent(textContent)
                        .tokenUsage(usage)
                        .finishReason("STOP")
                        .latencyMs(latencyMs)
                        .providerUsed(ModelProvider.GEMINI)
                        .modelNameUsed(model)
                        .build();
            } catch (Exception ex) {
                log.warn("Gemini generateContent call failed (model={}): {}", model, ex.getMessage());
                throw new IllegalStateException("Gemini generation failed: " + ex.getMessage(), ex);
            }
        }

        String fallbackText = buildDeterministicTextResponse(request);
        TokenUsage usage = estimateTokenUsage(request != null ? request.userPrompt() : "", fallbackText);
        long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);

        return LlmResponse.builder()
                .textContent(fallbackText)
                .tokenUsage(usage)
                .finishReason("STOP")
                .latencyMs(latencyMs)
                .providerUsed(ModelProvider.GEMINI)
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
        String cleanedJson = stripMarkdownJsonFence(rawResponse.textContent());
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
                    "Failed to deserialize Gemini structured response into " + responseClass.getSimpleName(), ex);
        }
    }

    @Override
    public float[] embed(String text, EmbeddingTaskType taskType) {
        int targetDims = configuredDimensions();
        Client client = getActiveClientIfConfigured();
        if (client != null) {
            try {
                EmbedContentConfig config = EmbedContentConfig.builder()
                        .outputDimensionality(targetDims)
                        .taskType(taskType != null ? taskType.name() : EmbeddingTaskType.RETRIEVAL_DOCUMENT.name())
                        .build();

                EmbedContentResponse response = client.models.embedContent(
                        modelName(),
                        text != null ? text : "",
                        config
                );

                Optional<List<ContentEmbedding>> embeddingsOpt = response.embeddings();
                if (embeddingsOpt.isPresent() && !embeddingsOpt.get().isEmpty()) {
                    Optional<List<Float>> valuesOpt = embeddingsOpt.get().getFirst().values();
                    if (valuesOpt.isPresent() && !valuesOpt.get().isEmpty()) {
                        List<Float> floatList = valuesOpt.get();
                        float[] raw = new float[floatList.size()];
                        for (int i = 0; i < floatList.size(); i++) {
                            raw[i] = floatList.get(i) != null ? floatList.get(i) : 0.0f;
                        }
                        return EmbeddingClient.normalizeL2(raw, targetDims);
                    }
                }
            } catch (Exception ex) {
                log.warn("Gemini embedContent call failed, using deterministic L2-normalized vector: {}", ex.getMessage());
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
        Client client = getActiveClientIfConfigured();
        String visionModel = StringUtils.hasText(aiProperties.getGemini().getVisionModel())
                ? aiProperties.getGemini().getVisionModel()
                : "gemini-2.5-flash";

        if (client != null && compressedWebpImageBytes != null && compressedWebpImageBytes.length > 0) {
            try {
                String effectiveMime = StringUtils.hasText(mimeType) ? mimeType : "image/webp";
                String fullPrompt = buildVisionPrompt(cvMetrics, specificPrompt);
                Content content = Content.fromParts(
                        Part.fromBytes(compressedWebpImageBytes, effectiveMime),
                        Part.fromText(fullPrompt)
                );
                GenerateContentConfig config = GenerateContentConfig.builder()
                        .temperature(0.15f)
                        .responseMimeType("application/json")
                        .build();

                GenerateContentResponse response = client.models.generateContent(visionModel, content, config);
                if (response.text() != null && !response.text().isBlank()) {
                    return objectMapper.readValue(
                            stripMarkdownJsonFence(response.text()),
                            VisionDiagnosticResult.class
                    );
                }
            } catch (Exception ex) {
                log.warn("Gemini vision analysis failed, falling back to heuristic CV evaluation: {}", ex.getMessage());
            }
        }

        return buildHeuristicVisionResult(cvMetrics);
    }

    private Client getActiveClientIfConfigured() {
        String apiKey = aiProperties.getGemini().getApiKey();
        if (!StringUtils.hasText(apiKey)
                || apiKey.startsWith("your-")
                || apiKey.startsWith("${")
                || "dummy".equalsIgnoreCase(apiKey)
                || "test".equalsIgnoreCase(apiKey)) {
            return null;
        }
        return geminiClientProvider.getIfAvailable();
    }

    private String resolveChatModel(LlmPromptRequest request) {
        if (request != null && StringUtils.hasText(request.promptKey())
                && request.promptKey().toUpperCase().contains("COMPLEX")) {
            String pro = aiProperties.getGemini().getProModel();
            if (StringUtils.hasText(pro)) {
                return pro;
            }
        }
        String chat = aiProperties.getGemini().getChatModel();
        return StringUtils.hasText(chat) ? chat : "gemini-2.5-flash";
    }

    private static String buildCombinedPromptText(LlmPromptRequest request) {
        if (request == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (request.history() != null && !request.history().isEmpty()) {
            for (LlmMessage msg : request.history()) {
                sb.append("[").append(msg.role()).append("]: ").append(msg.content()).append("\n");
            }
        }
        if (StringUtils.hasText(request.userPrompt())) {
            sb.append(request.userPrompt());
        }
        return sb.toString();
    }

    private static TokenUsage extractTokenUsage(
            GenerateContentResponse response,
            String promptText,
            String outputText
    ) {
        Optional<GenerateContentResponseUsageMetadata> usageOpt = response.usageMetadata();
        if (usageOpt.isPresent()) {
            GenerateContentResponseUsageMetadata meta = usageOpt.get();
            int promptTokens = meta.promptTokenCount().orElse(0);
            int completionTokens = meta.candidatesTokenCount().orElse(0);
            return TokenUsage.of(promptTokens, completionTokens);
        }
        return estimateTokenUsage(promptText, outputText);
    }

    static TokenUsage estimateTokenUsage(String promptText, String outputText) {
        int promptTokens = promptText != null ? Math.max(1, promptText.length() / 4) : 1;
        int completionTokens = outputText != null ? Math.max(1, outputText.length() / 4) : 1;
        return TokenUsage.of(promptTokens, completionTokens);
    }

    private static String buildDeterministicTextResponse(LlmPromptRequest request) {
        if (request != null && StringUtils.hasText(request.responseSchemaJson())) {
            return "{}";
        }
        String userPrompt = request != null && request.userPrompt() != null ? request.userPrompt() : "";
        return "Phản hồi kỹ thuật từ Gemini Assistant cho yêu cầu: " + userPrompt;
    }

    static String stripMarkdownJsonFence(String text) {
        if (text == null) {
            return "{}";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3).trim();
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    private static String buildVisionPrompt(ClassicalCvMetrics m, String specificPrompt) {
        if (m == null) {
            return specificPrompt != null ? specificPrompt : "Analyze film scan quality.";
        }
        return String.format(
                "Classical CV Stage-1 Metrics: laplacian=%.2f, luminance=%.2f, darkRatio=%.3f, brightRatio=%.3f, dustCount=%d, colorCast=%.2f. %s",
                m.laplacianSharpnessScore(),
                m.exposureMeanLuminance(),
                m.darkPixelRatio(),
                m.brightPixelRatio(),
                m.dustScratchBlobCount(),
                m.colorCastCieScore(),
                specificPrompt != null ? specificPrompt : ""
        );
    }

    static VisionDiagnosticResult buildHeuristicVisionResult(ClassicalCvMetrics m) {
        if (m == null) {
            return VisionDiagnosticResult.builder()
                    .overallScore(85.0)
                    .detectedDefect("GOOD")
                    .defectAttribution("NONE")
                    .visionCritiqueVi("Ảnh scan đạt tiêu chuẩn kỹ thuật tốt.")
                    .suggestedSolutions(List.of())
                    .confidenceScore(0.85)
                    .build();
        }

        if (m.dustScratchBlobCount() >= 15) {
            return VisionDiagnosticResult.builder()
                    .overallScore(48.0)
                    .detectedDefect("DUST_SCRATCH")
                    .defectAttribution("LAB_SCANNER_DEFECT")
                    .visionCritiqueVi("Phát hiện nhiều hạt bụi và vết xước dọc trên mặt film do khâu vệ sinh buồng scan.")
                    .suggestedSolutions(List.of("Yêu cầu Lab thổi bụi bằng bóng khí và scan lại với Digital ICE."))
                    .confidenceScore(0.91)
                    .build();
        }
        if (m.laplacianSharpnessScore() < 35.0) {
            return VisionDiagnosticResult.builder()
                    .overallScore(52.0)
                    .detectedDefect("BLURRY")
                    .defectAttribution("PHOTOGRAPHER_SHOOTING_ERROR")
                    .visionCritiqueVi("Khung hình bị mất nét hoặc rung tay khi chụp ở tốc độ màn trập thấp.")
                    .suggestedSolutions(List.of("Sử dụng tốc độ màn trập tối thiểu 1/focal_length hoặc chân máy."))
                    .confidenceScore(0.88)
                    .build();
        }
        if (m.darkPixelRatio() > 0.65 || m.exposureMeanLuminance() < 40.0) {
            return VisionDiagnosticResult.builder()
                    .overallScore(55.0)
                    .detectedDefect("UNDEREXPOSED")
                    .defectAttribution("PHOTOGRAPHER_SHOOTING_ERROR")
                    .visionCritiqueVi("Ảnh thiếu sáng nặng vùng tối, hạt film (grain) nổi nhiều ở vùng shadow.")
                    .suggestedSolutions(List.of("Đo sáng vào vùng tối (shadow) khi chụp film âm bản màu."))
                    .confidenceScore(0.89)
                    .build();
        }

        return VisionDiagnosticResult.builder()
                .overallScore(88.0)
                .detectedDefect("GOOD")
                .defectAttribution("NONE")
                .visionCritiqueVi("Khung hình có độ nét, phơi sáng và cân bằng màu sắc đạt chuẩn.")
                .suggestedSolutions(List.of())
                .confidenceScore(0.92)
                .build();
    }
}

