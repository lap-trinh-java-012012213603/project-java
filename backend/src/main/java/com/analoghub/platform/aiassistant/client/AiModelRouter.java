package com.analoghub.platform.aiassistant.client;

import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.service.governance.AiUsageLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class AiModelRouter {

    public static final String FEATURE_RAG_CHAT = "RAG_CHAT";
    public static final String FEATURE_RAG_COMPLEX = "RAG_COMPLEX";
    public static final String FEATURE_FILM_ADVISOR = "FILM_ADVISOR";
    public static final String FEATURE_SCAN_QC_VISION = "SCAN_QC_VISION";
    public static final String FEATURE_EMBEDDING = "EMBEDDING";

    private final AiProperties aiProperties;
    private final AiUsageLogger aiUsageLogger;
    private final Map<ModelProvider, LlmClient> llmClients = new EnumMap<>(ModelProvider.class);
    private final Map<ModelProvider, EmbeddingClient> embeddingClients = new EnumMap<>(ModelProvider.class);
    private final Map<ModelProvider, VisionLlmClient> visionClients = new EnumMap<>(ModelProvider.class);

    public AiModelRouter(
            AiProperties aiProperties,
            AiUsageLogger aiUsageLogger,
            List<LlmClient> llmClientList,
            List<EmbeddingClient> embeddingClientList,
            List<VisionLlmClient> visionClientList
    ) {
        this.aiProperties = aiProperties;
        this.aiUsageLogger = aiUsageLogger;

        if (llmClientList != null) {
            for (LlmClient client : llmClientList) {
                llmClients.put(client.provider(), client);
            }
        }
        if (embeddingClientList != null) {
            for (EmbeddingClient client : embeddingClientList) {
                embeddingClients.put(client.provider(), client);
            }
        }
        if (visionClientList != null) {
            for (VisionLlmClient client : visionClientList) {
                visionClients.put(client.provider(), client);
            }
        }
    }

    public LlmResponse generate(UUID userId, String featureType, LlmPromptRequest request) {
        ModelProvider primaryProvider = aiProperties.resolvePrimaryProvider();
        LlmClient primaryClient = getLlmClient(primaryProvider);

        try {
            LlmResponse response = primaryClient.generate(request);
            aiUsageLogger.logUsage(
                    userId,
                    featureType,
                    response.providerUsed(),
                    response.modelNameUsed(),
                    response.tokenUsage(),
                    response.latencyMs()
            );
            return response;
        } catch (Exception primaryEx) {
            if (!aiProperties.isFallbackEnabled()) {
                throw primaryEx;
            }
            ModelProvider fallbackProvider = getFallbackProvider(primaryProvider);
            LlmClient fallbackClient = llmClients.get(fallbackProvider);
            if (fallbackClient == null) {
                throw primaryEx;
            }
            log.warn("Primary LLM provider {} failed for feature={}, falling back to {}: {}",
                    primaryProvider, featureType, fallbackProvider, primaryEx.getMessage());

            LlmResponse fallbackResponse = fallbackClient.generate(request);
            aiUsageLogger.logUsage(
                    userId,
                    featureType,
                    fallbackResponse.providerUsed(),
                    fallbackResponse.modelNameUsed(),
                    fallbackResponse.tokenUsage(),
                    fallbackResponse.latencyMs()
            );
            return fallbackResponse;
        }
    }

    public <T> StructuredLlmResponse<T> generateStructured(
            UUID userId,
            String featureType,
            LlmPromptRequest request,
            Class<T> responseClass
    ) {
        ModelProvider primaryProvider = aiProperties.resolvePrimaryProvider();
        LlmClient primaryClient = getLlmClient(primaryProvider);

        try {
            StructuredLlmResponse<T> response = primaryClient.generateStructured(request, responseClass);
            aiUsageLogger.logUsage(
                    userId,
                    featureType,
                    response.providerUsed(),
                    response.modelNameUsed(),
                    response.tokenUsage(),
                    response.latencyMs()
            );
            return response;
        } catch (Exception primaryEx) {
            if (!aiProperties.isFallbackEnabled()) {
                throw primaryEx;
            }
            ModelProvider fallbackProvider = getFallbackProvider(primaryProvider);
            LlmClient fallbackClient = llmClients.get(fallbackProvider);
            if (fallbackClient == null) {
                throw primaryEx;
            }
            log.warn("Primary structured LLM provider {} failed for feature={}, falling back to {}: {}",
                    primaryProvider, featureType, fallbackProvider, primaryEx.getMessage());

            StructuredLlmResponse<T> fallbackResponse = fallbackClient.generateStructured(request, responseClass);
            aiUsageLogger.logUsage(
                    userId,
                    featureType,
                    fallbackResponse.providerUsed(),
                    fallbackResponse.modelNameUsed(),
                    fallbackResponse.tokenUsage(),
                    fallbackResponse.latencyMs()
            );
            return fallbackResponse;
        }
    }

    /**
     * Enforces Vector Space Invariance (Section 5.2): never cross-provider fallback for embeddings.
     */
    public float[] embed(UUID userId, String text, EmbeddingTaskType taskType) {
        long startNanos = System.nanoTime();
        ModelProvider embeddingProvider = aiProperties.resolveEmbeddingProvider();
        EmbeddingClient client = getEmbeddingClient(embeddingProvider);

        float[] rawOrNormalized = client.embed(text, taskType);
        float[] aligned = EmbeddingClient.normalizeL2(rawOrNormalized, EmbeddingClient.REQUIRED_DIMENSIONS);

        long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
        int estimatedInputTokens = text != null ? Math.max(1, text.length() / 4) : 1;
        aiUsageLogger.logUsage(
                userId,
                FEATURE_EMBEDDING,
                client.provider(),
                client.modelName(),
                TokenUsage.of(estimatedInputTokens, 0),
                latencyMs
        );
        return aligned;
    }

    public List<float[]> embedBatch(UUID userId, List<String> texts, EmbeddingTaskType taskType) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        long startNanos = System.nanoTime();
        ModelProvider embeddingProvider = aiProperties.resolveEmbeddingProvider();
        EmbeddingClient client = getEmbeddingClient(embeddingProvider);

        List<float[]> rawBatch = client.embedBatch(texts, taskType);
        List<float[]> alignedBatch = rawBatch.stream()
                .map(v -> EmbeddingClient.normalizeL2(v, EmbeddingClient.REQUIRED_DIMENSIONS))
                .toList();

        long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
        int totalChars = texts.stream().mapToInt(t -> t != null ? t.length() : 0).sum();
        int estimatedInputTokens = Math.max(texts.size(), totalChars / 4);

        aiUsageLogger.logUsage(
                userId,
                FEATURE_EMBEDDING,
                client.provider(),
                client.modelName(),
                TokenUsage.of(estimatedInputTokens, 0),
                latencyMs
        );
        return alignedBatch;
    }

    public VisionDiagnosticResult analyzeScanFrame(
            UUID userId,
            byte[] compressedWebpImageBytes,
            String mimeType,
            ClassicalCvMetrics cvMetrics,
            String specificPrompt
    ) {
        long startNanos = System.nanoTime();
        ModelProvider primaryProvider = aiProperties.resolvePrimaryProvider();
        VisionLlmClient primaryClient = getVisionClient(primaryProvider);

        try {
            VisionDiagnosticResult result = primaryClient.analyzeScanFrame(
                    compressedWebpImageBytes, mimeType, cvMetrics, specificPrompt);
            long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
            String visionModel = primaryProvider == ModelProvider.GEMINI
                    ? aiProperties.getGemini().getVisionModel()
                    : aiProperties.getOpenai().getVisionModel();
            aiUsageLogger.logUsage(
                    userId,
                    FEATURE_SCAN_QC_VISION,
                    primaryProvider,
                    visionModel,
                    TokenUsage.of(320, 120),
                    latencyMs
            );
            return result;
        } catch (Exception primaryEx) {
            if (!aiProperties.isFallbackEnabled()) {
                throw primaryEx;
            }
            ModelProvider fallbackProvider = getFallbackProvider(primaryProvider);
            VisionLlmClient fallbackClient = visionClients.get(fallbackProvider);
            if (fallbackClient == null) {
                throw primaryEx;
            }
            log.warn("Primary Vision provider {} failed, falling back to {}: {}",
                    primaryProvider, fallbackProvider, primaryEx.getMessage());

            VisionDiagnosticResult fallbackResult = fallbackClient.analyzeScanFrame(
                    compressedWebpImageBytes, mimeType, cvMetrics, specificPrompt);
            long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
            String visionModel = fallbackProvider == ModelProvider.GEMINI
                    ? aiProperties.getGemini().getVisionModel()
                    : aiProperties.getOpenai().getVisionModel();
            aiUsageLogger.logUsage(
                    userId,
                    FEATURE_SCAN_QC_VISION,
                    fallbackProvider,
                    visionModel,
                    TokenUsage.of(320, 120),
                    latencyMs
            );
            return fallbackResult;
        }
    }

    public LlmClient getLlmClient(ModelProvider provider) {
        LlmClient client = llmClients.get(provider);
        if (client == null) {
            throw new IllegalStateException("No LlmClient registered for provider: " + provider);
        }
        return client;
    }

    public EmbeddingClient getEmbeddingClient(ModelProvider provider) {
        EmbeddingClient client = embeddingClients.get(provider);
        if (client == null) {
            throw new IllegalStateException("No EmbeddingClient registered for provider: " + provider);
        }
        return client;
    }

    public VisionLlmClient getVisionClient(ModelProvider provider) {
        VisionLlmClient client = visionClients.get(provider);
        if (client == null) {
            throw new IllegalStateException("No VisionLlmClient registered for provider: " + provider);
        }
        return client;
    }

    private static ModelProvider getFallbackProvider(ModelProvider primary) {
        return primary == ModelProvider.GEMINI ? ModelProvider.OPENAI : ModelProvider.GEMINI;
    }
}

