package com.analoghub.platform.aiassistant.config;

import com.analoghub.platform.aiassistant.client.ModelProvider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    public static final int STANDARD_EMBEDDING_DIMENSIONS = 768;

    private ModelProvider defaultProvider = ModelProvider.GEMINI;
    private ModelProvider activeProvider;
    private ModelProvider embeddingProvider = ModelProvider.GEMINI;
    private boolean fallbackEnabled = true;

    private GeminiProperties gemini = new GeminiProperties();
    private OpenAiProperties openai = new OpenAiProperties();
    private RateLimitProperties rateLimit = new RateLimitProperties();
    private BulkheadProperties bulkhead = new BulkheadProperties();

    public ModelProvider resolvePrimaryProvider() {
        if (activeProvider != null) {
            return activeProvider;
        }
        return defaultProvider != null ? defaultProvider : ModelProvider.GEMINI;
    }

    public ModelProvider resolveEmbeddingProvider() {
        return embeddingProvider != null ? embeddingProvider : ModelProvider.GEMINI;
    }

    @Getter
    @Setter
    public static class GeminiProperties {
        private String apiKey;
        private String chatModel = "gemini-2.5-flash";
        private String proModel = "gemini-2.5-pro";
        private String visionModel = "gemini-2.5-flash";
        private String embeddingModel = "text-embedding-004";
        private int embeddingDimensions = STANDARD_EMBEDDING_DIMENSIONS;
        private double temperature = 0.2;
        private int maxOutputTokens = 2048;
    }

    @Getter
    @Setter
    public static class OpenAiProperties {
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private String chatModel = "gpt-4o-mini";
        private String proModel = "gpt-4o";
        private String visionModel = "gpt-4o-mini";
        private String embeddingModel = "text-embedding-3-small";
        private int embeddingDimensions = STANDARD_EMBEDDING_DIMENSIONS;
        private double temperature = 0.15;
        private int maxOutputTokens = 1024;
        private int timeoutSeconds = 30;
    }

    @Getter
    @Setter
    public static class RateLimitProperties {
        private int requestsPerMinute = 20;
        private int requestsPerDay = 200;
        private int maxDailyTokensPerUser = 100000;
    }

    @Getter
    @Setter
    public static class BulkheadProperties {
        private int maxConcurrentChats = 50;
        private int maxConcurrentVisions = 10;
        private int maxConcurrentEmbeddings = 20;
    }
}

