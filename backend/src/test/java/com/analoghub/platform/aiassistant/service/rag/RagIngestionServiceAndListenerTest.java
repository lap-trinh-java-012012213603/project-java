package com.analoghub.platform.aiassistant.service.rag;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.GeminiModelAdapter;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.client.OpenAiModelAdapter;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.KnowledgeChunk;
import com.analoghub.platform.aiassistant.domain.entity.AiUsageLog;
import com.analoghub.platform.aiassistant.listener.RagKnowledgeSyncListener;
import com.analoghub.platform.aiassistant.repository.AiUsageLogRepository;
import com.analoghub.platform.aiassistant.repository.KnowledgeChunkRepository;
import com.analoghub.platform.aiassistant.service.governance.AiCostEstimator;
import com.analoghub.platform.aiassistant.service.governance.AiUsageLogger;
import com.analoghub.platform.community.event.ArticleApprovedForRagEvent;
import com.analoghub.platform.community.event.ArticleRetractedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RagIngestionServiceAndListenerTest {

    private final List<KnowledgeChunk> inMemoryChunks = new ArrayList<>();
    private final List<AiUsageLog> inMemoryUsageLogs = new ArrayList<>();

    private MarkdownHeadingChunker chunker;
    private KnowledgeChunkRepository knowledgeChunkRepository;
    private RagIngestionService ragIngestionService;
    private RagKnowledgeSyncListener ragKnowledgeSyncListener;

    @BeforeEach
    void setUp() {
        inMemoryChunks.clear();
        inMemoryUsageLogs.clear();

        chunker = new MarkdownHeadingChunker();
        knowledgeChunkRepository = createInMemoryKnowledgeChunkRepository();
        AiUsageLogRepository usageLogRepository = createInMemoryUsageLogRepository();

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

        ragIngestionService = new RagIngestionService(
                chunker,
                knowledgeChunkRepository,
                router,
                aiProperties
        );
        ragKnowledgeSyncListener = new RagKnowledgeSyncListener(ragIngestionService);
    }

    @Test
    void onArticleApprovedForRag_splitsEmbedsAndUpsertsChunks_andSkipsUnchangedHashOnUpdate() {
        UUID articleId = UUID.randomUUID();
        String slug = "ky-thuat-trang-push-kodak-tri-x-400";
        String title = "Kỹ thuật Tráng Push Kodak Tri-X 400";

        StringBuilder sec1 = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            sec1.append("Khi chụp Kodak Tri-X 400 ở EI 1600 tức là Push +2 stops, thời gian tráng với thuốc D-76 Stock ")
                    .append("ở nhiệt độ chuẩn 20 độ C là 9 phút 30 giây trong bình nhỏ. ");
        }

        StringBuilder sec2 = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            sec2.append("Sau bước Developer, sử dụng dung dịch Stop Bath Axit Acetic 1.5% trong 30 giây và định hình ")
                    .append("bằng Kodak Rapid Fixer trong 4 phút để âm bản trong trẻo. ");
        }

        String initialMarkdown = """
                # Kỹ thuật Tráng Push Kodak Tri-X 400
                ## 1. Thông số Thời gian Developer D-76
                %s
                ## 2. Quy trình Stop Bath và Fixer
                %s
                """.formatted(sec1, sec2);

        ArticleApprovedForRagEvent approveEvent = new ArticleApprovedForRagEvent(
                articleId,
                slug,
                title,
                "DARKROOM_TECHNIQUE",
                initialMarkdown
        );

        // 1. Initial approval -> all chunks are embedded and saved
        ragKnowledgeSyncListener.onArticleApprovedForRag(approveEvent);

        assertThat(inMemoryChunks).hasSizeGreaterThanOrEqualTo(2);
        int initialChunkCount = inMemoryChunks.size();

        KnowledgeChunk firstChunk = inMemoryChunks.getFirst();
        assertThat(firstChunk.getSourceType()).isEqualTo(RagIngestionService.SOURCE_TYPE_EXPERT_ARTICLE);
        assertThat(firstChunk.getSourceRefId()).isEqualTo(articleId);
        assertThat(firstChunk.getChunkIndex()).isZero();
        assertThat(firstChunk.getContentSha256()).hasSize(64);
        assertThat(firstChunk.getEmbeddingModel()).isEqualTo("text-embedding-004");
        assertThat(firstChunk.getDimensions()).isEqualTo(768);
        assertThat(firstChunk.getEmbedding()).hasSize(768);
        assertThat(firstChunk.getMetadata())
                .containsEntry("source", "COMMUNITY_EXPERT")
                .containsEntry("articleSlug", slug)
                .containsEntry("category", "DARKROOM_TECHNIQUE");

        double normSq = 0.0;
        for (float v : firstChunk.getEmbedding()) {
            normSq += (double) v * v;
        }
        assertThat(Math.sqrt(normSq)).isCloseTo(1.0, within(1e-5));

        // 2. Re-ingest identical content -> 0 chunks re-embedded, all skipped by content_sha256 check
        RagIngestionService.IngestionResult unchangedResult = ragIngestionService.ingestArticleContent(
                articleId,
                slug,
                title,
                "DARKROOM_TECHNIQUE",
                initialMarkdown
        );
        assertThat(unchangedResult.embeddedAndUpsertedCount()).isZero();
        assertThat(unchangedResult.skippedUnchangedCount()).isEqualTo(initialChunkCount);
        assertThat(unchangedResult.deletedStaleCount()).isZero();

        // 3. Shorten article to only section 1 -> stale trailing chunk(s) deleted
        String shortenedMarkdown = """
                # Kỹ thuật Tráng Push Kodak Tri-X 400
                ## 1. Thông số Thời gian Developer D-76
                %s
                """.formatted(sec1);

        RagIngestionService.IngestionResult shortenedResult = ragIngestionService.ingestArticleContent(
                articleId,
                slug,
                title,
                "DARKROOM_TECHNIQUE",
                shortenedMarkdown
        );
        assertThat(shortenedResult.totalChunks()).isLessThan(initialChunkCount);
        assertThat(shortenedResult.deletedStaleCount()).isGreaterThanOrEqualTo(1);
        assertThat(inMemoryChunks).hasSize(shortenedResult.totalChunks());

        // 4. Retract article -> all chunks for articleId are deleted
        ArticleRetractedEvent retractEvent = new ArticleRetractedEvent(articleId, slug);
        ragKnowledgeSyncListener.onArticleRetracted(retractEvent);

        assertThat(inMemoryChunks).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private KnowledgeChunkRepository createInMemoryKnowledgeChunkRepository() {
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
                            inMemoryChunks.removeIf(existing ->
                                    existing.getId().equals(entity.getId())
                                            || (existing.getSourceType().equals(entity.getSourceType())
                                            && existing.getSourceRefId().equals(entity.getSourceRefId())
                                            && existing.getChunkIndex().equals(entity.getChunkIndex())));
                            inMemoryChunks.add(entity);
                            saved.add(entity);
                        }
                        return saved;
                    }
                    if ("deleteBySourceTypeAndSourceRefId".equals(name)) {
                        String sourceType = (String) args[0];
                        UUID sourceRefId = (UUID) args[1];
                        int before = inMemoryChunks.size();
                        inMemoryChunks.removeIf(c -> sourceType.equals(c.getSourceType())
                                && sourceRefId.equals(c.getSourceRefId()));
                        return before - inMemoryChunks.size();
                    }
                    if ("deleteBySourceTypeAndSourceRefIdAndChunkIndexGreaterThanEqual".equals(name)) {
                        String sourceType = (String) args[0];
                        UUID sourceRefId = (UUID) args[1];
                        int minIdx = (Integer) args[2];
                        int before = inMemoryChunks.size();
                        inMemoryChunks.removeIf(c -> sourceType.equals(c.getSourceType())
                                && sourceRefId.equals(c.getSourceRefId())
                                && c.getChunkIndex() != null
                                && c.getChunkIndex() >= minIdx);
                        return before - inMemoryChunks.size();
                    }
                    if ("findAll".equals(name)) {
                        return List.copyOf(inMemoryChunks);
                    }
                    return null;
                }
        );
    }

    private AiUsageLogRepository createInMemoryUsageLogRepository() {
        return (AiUsageLogRepository) Proxy.newProxyInstance(
                AiUsageLogRepository.class.getClassLoader(),
                new Class<?>[]{AiUsageLogRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        AiUsageLog log = (AiUsageLog) args[0];
                        if (log.getId() == null) {
                            log.setId(UUID.randomUUID());
                        }
                        inMemoryUsageLogs.add(log);
                        return log;
                    }
                    return null;
                }
        );
    }
}
