package com.analoghub.platform.aiassistant.client;

import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.entity.AiUsageLog;
import com.analoghub.platform.aiassistant.repository.AiUsageLogRepository;
import com.analoghub.platform.aiassistant.service.governance.AiCostEstimator;
import com.analoghub.platform.aiassistant.service.governance.AiUsageLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelRouterAndAdaptersTest {

    @Mock
    private ObjectProvider<Client> geminiClientProvider;

    @Mock
    private AiUsageLogRepository aiUsageLogRepository;

    private AiProperties aiProperties;
    private AiUsageLogger aiUsageLogger;
    private GeminiModelAdapter geminiAdapter;
    private OpenAiModelAdapter openAiAdapter;
    private AiModelRouter aiModelRouter;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setDefaultProvider(ModelProvider.GEMINI);
        aiProperties.setEmbeddingProvider(ModelProvider.GEMINI);
        aiProperties.setFallbackEnabled(true);
        aiProperties.getGemini().setApiKey("test");
        aiProperties.getOpenai().setApiKey("test");

        ObjectMapper objectMapper = new ObjectMapper();
        AiCostEstimator costEstimator = new AiCostEstimator();
        aiUsageLogger = new AiUsageLogger(aiUsageLogRepository, costEstimator);

        geminiAdapter = new GeminiModelAdapter(geminiClientProvider, aiProperties, objectMapper);
        openAiAdapter = new OpenAiModelAdapter(aiProperties, objectMapper);

        aiModelRouter = new AiModelRouter(
                aiProperties,
                aiUsageLogger,
                List.of(geminiAdapter, openAiAdapter),
                List.of(geminiAdapter, openAiAdapter),
                List.of(geminiAdapter, openAiAdapter)
        );
    }

    @Test
    void embed_returnsL2Normalized768DimensionVectorAndLogsUsage() {
        when(aiUsageLogRepository.save(any(AiUsageLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UUID userId = UUID.randomUUID();
        float[] vector = aiModelRouter.embed(
                userId,
                "Cách kiểm soát nhiệt độ thuốc tráng C-41 ở 38 độ C",
                EmbeddingTaskType.RETRIEVAL_QUERY
        );

        assertThat(vector).hasSize(768);

        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += (double) v * v;
        }
        double l2Norm = Math.sqrt(sumSquares);
        assertThat(l2Norm).isCloseTo(1.0, within(1e-5));

        ArgumentCaptor<AiUsageLog> logCaptor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(aiUsageLogRepository, atLeastOnce()).save(logCaptor.capture());

        AiUsageLog logged = logCaptor.getValue();
        assertThat(logged.getUserId()).isEqualTo(userId);
        assertThat(logged.getAction()).contains("EMBEDDING:GEMINI:text-embedding-004");
        assertThat(logged.getPromptTokens()).isGreaterThan(0);
    }

    @Test
    void generate_whenPrimaryProviderFails_fallsBackToSecondaryProviderAndLogsUsage() {
        when(aiUsageLogRepository.save(any(AiUsageLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LlmClient failingGemini = mock(LlmClient.class);
        when(failingGemini.provider()).thenReturn(ModelProvider.GEMINI);
        when(failingGemini.generate(any())).thenThrow(new IllegalStateException("Gemini 503 Unavailable"));

        AiModelRouter routerWithFailingPrimary = new AiModelRouter(
                aiProperties,
                aiUsageLogger,
                List.of(failingGemini, openAiAdapter),
                List.of(geminiAdapter, openAiAdapter),
                List.of(geminiAdapter, openAiAdapter)
        );

        LlmPromptRequest request = LlmPromptRequest.builder()
                .systemInstruction("Bạn là chuyên gia buồng tối.")
                .userPrompt("Hóa chất C-41 cần lưu ý gì?")
                .build();

        LlmResponse response = routerWithFailingPrimary.generate(
                UUID.randomUUID(),
                AiModelRouter.FEATURE_RAG_CHAT,
                request
        );

        assertThat(response.providerUsed()).isEqualTo(ModelProvider.OPENAI);
        assertThat(response.modelNameUsed()).isEqualTo("gpt-4o-mini");
        assertThat(response.textContent()).isNotBlank();
    }
}

