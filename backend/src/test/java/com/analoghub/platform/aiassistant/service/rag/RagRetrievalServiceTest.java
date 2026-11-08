package com.analoghub.platform.aiassistant.service.rag;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.GeminiModelAdapter;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.client.OpenAiModelAdapter;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.KnowledgeChunk;
import com.analoghub.platform.aiassistant.domain.entity.AiUsageLog;
import com.analoghub.platform.aiassistant.repository.AiUsageLogRepository;
import com.analoghub.platform.aiassistant.repository.KnowledgeChunkRepository;
import com.analoghub.platform.aiassistant.service.governance.AiCostEstimator;
import com.analoghub.platform.aiassistant.service.governance.AiUsageLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalServiceTest {

    private final List<KnowledgeChunk> inMemoryChunks = new ArrayList<>();

    private MarkdownHeadingChunker chunker;
    private KnowledgeSeedLoader seedLoader;
    private RagIngestionService ingestionService;
    private RagRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        inMemoryChunks.clear();

        chunker = new MarkdownHeadingChunker();
        KnowledgeChunkRepository chunkRepository = createInMemoryChunkRepository();
        AiUsageLogRepository usageLogRepository = createNoOpUsageLogRepository();

        AiProperties aiProperties = new AiProperties();
        aiProperties.setDefaultProvider(ModelProvider.GEMINI);
        aiProperties.setEmbeddingProvider(ModelProvider.GEMINI);
        aiProperties.getGemini().setApiKey("test");
        aiProperties.getOpenai().setApiKey("test");

        ObjectMapper objectMapper = new ObjectMapper();
        AiUsageLogger usageLogger = new AiUsageLogger(usageLogRepository, new AiCostEstimator());

        GeminiModelAdapter geminiAdapter = new GeminiModelAdapter(null, aiProperties, objectMapper);
        OpenAiModelAdapter openAiAdapter = new OpenAiModelAdapter(aiProperties, objectMapper);

        AiModelRouter router = new AiModelRouter(
                aiProperties,
                usageLogger,
                List.of(geminiAdapter, openAiAdapter),
                List.of(geminiAdapter, openAiAdapter),
                List.of(geminiAdapter, openAiAdapter)
        );

        ingestionService = new RagIngestionService(chunker, chunkRepository, router, aiProperties);
        seedLoader = new KnowledgeSeedLoader(chunker);
        List<KnowledgeSeedLoader.SeedDocument> seeds = seedLoader.loadSeedDocuments();
        for (KnowledgeSeedLoader.SeedDocument seed : seeds) {
            ingestionService.ingestSeedDocument(seed);
        }

        retrievalService = new RagRetrievalService(
                chunkRepository,
                router,
                aiProperties,
                seedLoader,
                objectMapper
        );
    }

    @Test
    void retrieve_forInDomainC41Question_returnsRelevantTopChunksAboveThresholdWithoutAbstaining() {
        RagRetrievalService.RetrievalResult result = retrievalService.retrieve(
                UUID.randomUUID(),
                "Nhiệt độ dung dịch Developer của quy trình tráng C-41 bắt buộc duy trì ở bao nhiêu độ C và thời gian 3 phút 15 giây?"
        );

        assertThat(result.abstained()).isFalse();
        assertThat(result.abstentionMessageVi()).isNull();
        assertThat(result.topSimilarityScore()).isGreaterThanOrEqualTo(RagRetrievalService.DEFAULT_ABSTENTION_THRESHOLD);
        assertThat(result.selectedChunks()).isNotEmpty().hasSizeLessThanOrEqualTo(5);

        RagRetrievalService.ScoredChunk topChunk = result.selectedChunks().getFirst();
        assertThat(topChunk.chunkTitle()).contains("Quy trình tráng C-41");
        assertThat(topChunk.chunkContent()).contains("37.8°C");
    }

    @Test
    void retrieve_forOutOfDomainQuestion_triggersAbstentionWhenTopScoreBelow065() {
        RagRetrievalService.RetrievalResult footballResult = retrievalService.retrieve(
                UUID.randomUUID(),
                "Kết quả trận bóng đá ngoại hạng Anh tối hôm qua tỷ số bao nhiêu và đội nào vô địch World Cup?"
        );

        assertThat(footballResult.abstained()).isTrue();
        assertThat(footballResult.selectedChunks()).isEmpty();
        assertThat(footballResult.topSimilarityScore()).isLessThan(RagRetrievalService.DEFAULT_ABSTENTION_THRESHOLD);
        assertThat(footballResult.abstentionReason()).isEqualTo(RagRetrievalService.ABSTENTION_REASON_LOW_SIMILARITY);
        assertThat(footballResult.abstentionMessageVi())
                .isEqualTo(RagRetrievalService.DEFAULT_ABSTENTION_MESSAGE_VI)
                .contains("từ chối đưa ra suy đoán không có cơ sở kỹ thuật");
    }

    @Test
    void retrieve_withProcessTypeFilter_restrictsCandidatesToMatchingMetadata() {
        RagRetrievalService.RetrievalResult ecn2Result = retrievalService.retrieve(
                UUID.randomUUID(),
                "Cách tẩy lớp than chống lóa Remjet bằng dung dịch Borax PB-2 trước khi tráng",
                "ECN2",
                null,
                5
        );

        assertThat(ecn2Result.abstained()).isFalse();
        assertThat(ecn2Result.selectedChunks()).isNotEmpty();
        for (RagRetrievalService.ScoredChunk chunk : ecn2Result.selectedChunks()) {
            assertThat(chunk.metadata()).containsEntry("processType", "ECN2");
        }
    }

    @Test
    void rerankWithMmr_penalizesRedundantDuplicateChunksAndPromotesDiverseChunk() {
        RagRetrievalService.ScoredChunk primaryC41 = RagRetrievalService.ScoredChunk.builder()
                .chunkId(UUID.randomUUID())
                .chunkTitle("Quy trình C-41 > Nhiệt độ Developer")
                .chunkContent("Dung dịch Developer của quy trình C-41 bắt buộc duy trì ở nhiệt độ 37.8 độ C trong 3 phút 15 giây.")
                .similarityScore(0.92)
                .metadata(Map.of("processType", "C41"))
                .build();

        RagRetrievalService.ScoredChunk nearDuplicateC41 = RagRetrievalService.ScoredChunk.builder()
                .chunkId(UUID.randomUUID())
                .chunkTitle("Quy trình C-41 > Nhiệt độ Developer (Bản sao)")
                .chunkContent("Dung dịch Developer của quy trình C-41 bắt buộc duy trì ở nhiệt độ 37.8 độ C trong 3 phút 15 giây.")
                .similarityScore(0.90)
                .metadata(Map.of("processType", "C41"))
                .build();

        RagRetrievalService.ScoredChunk complementaryBleachFix = RagRetrievalService.ScoredChunk.builder()
                .chunkId(UUID.randomUUID())
                .chunkTitle("Quy trình C-41 > Tẩy bạc Bleach và Định hình Fixer")
                .chunkContent("Bước Bleach sử dụng Ferric Ammonium EDTA có sục khí oxy để oxy hóa bạc kim loại trước khi qua bể Fixer.")
                .similarityScore(0.82)
                .metadata(Map.of("processType", "C41"))
                .build();

        List<RagRetrievalService.ScoredChunk> reranked = retrievalService.rerankWithMmr(
                List.of(primaryC41, nearDuplicateC41, complementaryBleachFix),
                2,
                0.7
        );

        assertThat(reranked).hasSize(2);
        assertThat(reranked.get(0).chunkId()).isEqualTo(primaryC41.chunkId());
        // MMR (lambda=0.7) promotes complementaryBleachFix (0.7*0.82 - 0 = 0.574) over nearDuplicateC41 (0.7*0.90 - 0.3*1.0 = 0.33)
        assertThat(reranked.get(1).chunkId()).isEqualTo(complementaryBleachFix.chunkId());
    }

    @SuppressWarnings("unchecked")
    private KnowledgeChunkRepository createInMemoryChunkRepository() {
        return (KnowledgeChunkRepository) Proxy.newProxyInstance(
                KnowledgeChunkRepository.class.getClassLoader(),
                new Class<?>[]{KnowledgeChunkRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findBySourceTypeAndSourceRefId".equals(name)) {
                        String sourceType = (String) args[0];
                        UUID sourceRefId = (UUID) args[1];
                        return inMemoryChunks.stream()
                                .filter(c -> sourceType.equals(c.getSourceType())
                                        && sourceRefId.equals(c.getSourceRefId()))
                                .sorted(Comparator.comparingInt(c -> c.getChunkIndex() != null ? c.getChunkIndex() : 0))
                                .toList();
                    }
                    if ("saveAll".equals(name)) {
                        Iterable<KnowledgeChunk> entities = (Iterable<KnowledgeChunk>) args[0];
                        List<KnowledgeChunk> saved = new ArrayList<>();
                        for (KnowledgeChunk entity : entities) {
                            if (entity.getId() == null) {
                                entity.setId(UUID.randomUUID());
                            }
                            entity.syncFieldsToMetadata();
                            inMemoryChunks.add(entity);
                            saved.add(entity);
                        }
                        return saved;
                    }
                    if ("findAll".equals(name)) {
                        return List.copyOf(inMemoryChunks);
                    }
                    if ("findTopNearestByCosineDistance".equals(name)) {
                        return List.of();
                    }
                    return null;
                }
        );
    }

    private AiUsageLogRepository createNoOpUsageLogRepository() {
        return (AiUsageLogRepository) Proxy.newProxyInstance(
                AiUsageLogRepository.class.getClassLoader(),
                new Class<?>[]{AiUsageLogRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        return (AiUsageLog) args[0];
                    }
                    return null;
                }
        );
    }
}
