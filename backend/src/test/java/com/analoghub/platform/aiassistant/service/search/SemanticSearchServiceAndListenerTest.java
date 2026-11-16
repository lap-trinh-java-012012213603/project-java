package com.analoghub.platform.aiassistant.service.search;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.EmbeddingClient;
import com.analoghub.platform.aiassistant.client.GeminiModelAdapter;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.client.OpenAiModelAdapter;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.KnowledgeChunk;
import com.analoghub.platform.aiassistant.domain.LabEmbedding;
import com.analoghub.platform.aiassistant.domain.ListingEmbedding;
import com.analoghub.platform.aiassistant.domain.entity.AiUsageLog;
import com.analoghub.platform.aiassistant.dto.HybridSearchResponse;
import com.analoghub.platform.aiassistant.dto.HybridSearchResponse.HybridLabSearchResultDto;
import com.analoghub.platform.aiassistant.dto.HybridSearchScope;
import com.analoghub.platform.aiassistant.listener.LabAndListingEmbeddingListener;
import com.analoghub.platform.aiassistant.listener.LabAndListingEmbeddingListener.LabProfileSyncPayload;
import com.analoghub.platform.aiassistant.repository.AiUsageLogRepository;
import com.analoghub.platform.aiassistant.repository.KnowledgeChunkRepository;
import com.analoghub.platform.aiassistant.repository.LabEmbeddingRepository;
import com.analoghub.platform.aiassistant.repository.ListingEmbeddingRepository;
import com.analoghub.platform.aiassistant.service.governance.AiCostEstimator;
import com.analoghub.platform.aiassistant.service.governance.AiUsageLogger;
import com.analoghub.platform.aiassistant.service.rag.KnowledgeSeedLoader;
import com.analoghub.platform.aiassistant.service.rag.MarkdownHeadingChunker;
import com.analoghub.platform.aiassistant.service.rag.RagRetrievalService;
import com.analoghub.platform.aiassistant.service.search.SemanticSearchService.HybridSearchQuery;
import com.analoghub.platform.aiassistant.service.search.SemanticSearchService.LexicalLabHit;
import com.analoghub.platform.aiassistant.service.search.SemanticSearchService.VectorLabHit;
import com.analoghub.platform.filmlab.event.FilmLabCreatedEvent;
import com.analoghub.platform.filmlab.event.LabReviewPostedEvent;
import com.analoghub.platform.marketplace.event.ListingArchivedEvent;
import com.analoghub.platform.marketplace.event.ListingPublishedEvent;
import com.analoghub.platform.marketplace.event.ListingUpdatedEvent;
import com.analoghub.platform.search.dto.LabSearchHitDto;
import com.analoghub.platform.search.dto.LabSearchResponse;
import com.analoghub.platform.search.dto.ListingSearchHitDto;
import com.analoghub.platform.search.dto.ListingSearchResponse;
import com.analoghub.platform.search.facade.SearchFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticSearchServiceAndListenerTest {

    private final Map<UUID, LabEmbedding> labEmbeddingStore = new LinkedHashMap<>();
    private final Map<UUID, ListingEmbedding> listingEmbeddingStore = new LinkedHashMap<>();
    private final List<LabSearchHitDto> simulatedEsLabHits = new ArrayList<>();
    private final List<ListingSearchHitDto> simulatedEsListingHits = new ArrayList<>();
    private final AtomicBoolean simulateEsFailure = new AtomicBoolean(false);

    private LabAndListingEmbeddingListener embeddingListener;
    private SemanticSearchService semanticSearchService;

    @BeforeEach
    void setUp() {
        labEmbeddingStore.clear();
        listingEmbeddingStore.clear();
        simulatedEsLabHits.clear();
        simulatedEsListingHits.clear();
        simulateEsFailure.set(false);

        AiProperties aiProperties = new AiProperties();
        aiProperties.setActiveProvider(ModelProvider.GEMINI);
        aiProperties.setEmbeddingProvider(ModelProvider.GEMINI);
        aiProperties.getGemini().setApiKey("test");
        aiProperties.getOpenai().setApiKey("test");

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectProvider<Client> nullGeminiClientProvider = new ObjectProvider<>() {
            @Override
            public Client getObject(Object... args) {
                return null;
            }

            @Override
            public Client getIfAvailable() {
                return null;
            }

            @Override
            public Client getIfUnique() {
                return null;
            }

            @Override
            public Client getObject() {
                return null;
            }
        };

        GeminiModelAdapter geminiAdapter = new GeminiModelAdapter(nullGeminiClientProvider, aiProperties, objectMapper);
        OpenAiModelAdapter openAiAdapter = new OpenAiModelAdapter(aiProperties, objectMapper);

        AiUsageLogRepository usageLogRepository = createUsageLogRepositoryStub();
        AiCostEstimator costEstimator = new AiCostEstimator();
        AiUsageLogger usageLogger = new AiUsageLogger(usageLogRepository, costEstimator);

        AiModelRouter aiModelRouter = new AiModelRouter(
                aiProperties,
                usageLogger,
                List.of(geminiAdapter, openAiAdapter),
                List.of(geminiAdapter, openAiAdapter),
                List.of(geminiAdapter, openAiAdapter)
        );

        MarkdownHeadingChunker chunker = new MarkdownHeadingChunker();
        KnowledgeSeedLoader seedLoader = new KnowledgeSeedLoader(chunker);
        seedLoader.loadSeedDocuments();

        KnowledgeChunkRepository chunkRepository = createChunkRepositoryStub();
        RagRetrievalService ragRetrievalService = new RagRetrievalService(
                chunkRepository,
                aiModelRouter,
                aiProperties,
                seedLoader,
                objectMapper
        );

        LabEmbeddingRepository labRepo = createLabEmbeddingRepositoryStub();
        ListingEmbeddingRepository listingRepo = createListingEmbeddingRepositoryStub();
        SearchFacade searchFacade = createSearchFacadeStub();

        embeddingListener = new LabAndListingEmbeddingListener(labRepo, listingRepo, aiModelRouter);
        semanticSearchService = new SemanticSearchService(
                searchFacade,
                labRepo,
                listingRepo,
                embeddingListener,
                ragRetrievalService,
                aiModelRouter,
                aiProperties
        );
    }

    @Test
    void rrfFusionMatchesExactWorkedExampleFromSpecSection5_3() {
        UUID labA = UUID.fromString("0195d100-aaaa-7000-8000-000000000001");
        UUID labB = UUID.fromString("0195d100-bbbb-7000-8000-000000000002");
        UUID labC = UUID.fromString("0195d100-cccc-7000-8000-000000000003");
        UUID labD = UUID.fromString("0195d100-dddd-7000-8000-000000000004");
        UUID labE = UUID.fromString("0195d100-eeee-7000-8000-000000000005");

        // Section 5.3 Table:
        // Lab A: rank_ES = 1, rank_vec = 2 -> 1/61 + 1/62 = 0.03252 (#1)
        // Lab B: rank_ES = 8, rank_vec = 1 -> 1/68 + 1/61 = 0.03110 (#2)
        // Lab C: rank_ES = 2, rank_vec = 12 -> 1/62 + 1/72 = 0.03002 (#3)
        // Lab D: rank_ES = 3, rank_vec = null -> 1/63 + 0 = 0.01587 (#4)
        // Lab E: rank_ES = null, rank_vec = 4 -> 0 + 1/64 = 0.01563 (#5)
        List<LexicalLabHit> esHits = List.of(
                LexicalLabHit.builder().labId(labA).rank(1).name("Lab A").slug("lab-a").bm25Score(14.82).build(),
                LexicalLabHit.builder().labId(labC).rank(2).name("Lab C").slug("lab-c").bm25Score(13.10).build(),
                LexicalLabHit.builder().labId(labD).rank(3).name("Lab D").slug("lab-d").bm25Score(11.50).build(),
                LexicalLabHit.builder().labId(labB).rank(8).name("Lab B").slug("lab-b").bm25Score(9.20).build()
        );

        List<VectorLabHit> vecHits = List.of(
                VectorLabHit.builder().labId(labB).rank(1).name("Lab B").slug("lab-b").cosineSimilarity(0.912).build(),
                VectorLabHit.builder().labId(labA).rank(2).name("Lab A").slug("lab-a").cosineSimilarity(0.884).build(),
                VectorLabHit.builder().labId(labE).rank(4).name("Lab E").slug("lab-e").cosineSimilarity(0.810).build(),
                VectorLabHit.builder().labId(labC).rank(12).name("Lab C").slug("lab-c").cosineSimilarity(0.740).build()
        );

        List<HybridLabSearchResultDto> fused = semanticSearchService.mergeLabsRrf(esHits, vecHits, 60, 10);

        assertThat(fused).hasSize(5);

        assertThat(fused.get(0).finalRank()).isEqualTo(1);
        assertThat(fused.get(0).labId()).isEqualTo(labA);
        assertThat(fused.get(0).rrfFusionScore()).isEqualTo(0.03252);
        assertThat(fused.get(0).rankDetails().esBm25Rank()).isEqualTo(1);
        assertThat(fused.get(0).rankDetails().pgvectorRank()).isEqualTo(2);

        assertThat(fused.get(1).finalRank()).isEqualTo(2);
        assertThat(fused.get(1).labId()).isEqualTo(labB);
        assertThat(fused.get(1).rrfFusionScore()).isEqualTo(0.03110);
        assertThat(fused.get(1).rankDetails().esBm25Rank()).isEqualTo(8);
        assertThat(fused.get(1).rankDetails().pgvectorRank()).isEqualTo(1);

        assertThat(fused.get(2).finalRank()).isEqualTo(3);
        assertThat(fused.get(2).labId()).isEqualTo(labC);
        assertThat(fused.get(2).rrfFusionScore()).isEqualTo(0.03002);

        assertThat(fused.get(3).finalRank()).isEqualTo(4);
        assertThat(fused.get(3).labId()).isEqualTo(labD);
        assertThat(fused.get(3).rrfFusionScore()).isEqualTo(0.01587);
        assertThat(fused.get(3).rankDetails().pgvectorRank()).isNull();

        assertThat(fused.get(4).finalRank()).isEqualTo(5);
        assertThat(fused.get(4).labId()).isEqualTo(labE);
        assertThat(fused.get(4).rrfFusionScore()).isEqualTo(0.01563);
        assertThat(fused.get(4).rankDetails().esBm25Rank()).isNull();
    }

    @Test
    void labAndListingEmbeddingListenerSyncsAndRemovesVectorsOnDomainEvents() {
        UUID labId = UUID.randomUUID();
        embeddingListener.onFilmLabCreated(new FilmLabCreatedEvent(labId, "LLab Sài Gòn Analog"));

        assertThat(labEmbeddingStore).containsKey(labId);
        LabEmbedding labEmbedding = labEmbeddingStore.get(labId);
        assertThat(labEmbedding.getDescription()).contains("[Lab Profile] Tên: LLab Sài Gòn Analog");
        assertThat(labEmbedding.getEmbedding()).hasSize(EmbeddingClient.REQUIRED_DIMENSIONS);
        assertThat(computeL2Norm(labEmbedding.getEmbedding())).isBetween(0.999, 1.001);

        embeddingListener.onLabReviewPosted(new LabReviewPostedEvent(UUID.randomUUID(), labId, UUID.randomUUID()));
        assertThat(labEmbeddingStore).containsKey(labId);

        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        embeddingListener.onListingPublished(new ListingPublishedEvent(
                listingId,
                sellerId,
                "Máy ảnh Rangefinder Leica M6 TTL 0.72 Chrome",
                "CAMERA_BODY",
                BigDecimal.valueOf(68000000),
                "PUBLISHED"
        ));

        assertThat(listingEmbeddingStore).containsKey(listingId);
        ListingEmbedding listingEmbedding = listingEmbeddingStore.get(listingId);
        assertThat(listingEmbedding.getFeatures()).contains("Leica M6 TTL 0.72 Chrome");
        assertThat(listingEmbedding.getEmbedding()).hasSize(EmbeddingClient.REQUIRED_DIMENSIONS);

        embeddingListener.onListingUpdated(new ListingUpdatedEvent(
                listingId,
                sellerId,
                "Máy ảnh Rangefinder Leica M6 TTL 0.72 Chrome kèm hộp",
                "CAMERA_BODY",
                BigDecimal.valueOf(66500000),
                "PUBLISHED"
        ));
        assertThat(listingEmbeddingStore.get(listingId).getFeatures()).contains("kèm hộp");

        embeddingListener.onListingArchived(new ListingArchivedEvent(listingId, sellerId, "SOLD"));
        assertThat(listingEmbeddingStore).doesNotContainKey(listingId);
    }

    @Test
    void parallelVirtualThreadHybridSearchReturnsUnifiedLabAndListingResults() {
        UUID lab1Id = UUID.fromString("0195d100-aaaa-7000-8000-000000000001");
        UUID lab2Id = UUID.fromString("0195d100-cccc-7000-8000-000000000003");

        embeddingListener.upsertLabEmbedding(LabProfileSyncPayload.builder()
                .labId(lab1Id)
                .slug("llab-saigon-analog")
                .name("LLab Saigon Analog")
                .fullAddress("214/19 Nguyễn Văn Nguyễn, Quận 1, TP.HCM")
                .provinceCode("79")
                .supportedFormats(List.of("FMT_135", "FMT_120"))
                .supportedProcesses(List.of("C41", "ECN2", "BW"))
                .scannerModels(List.of("NORITSU_HS1800", "FRONTIER_SP3000"))
                .colorSignatureBio("Tráng film điện ảnh ECN-2 sạch lớp than Remjet, scan Noritsu HS-1800 màu trong trẻo")
                .estimatedTurnaroundHours(18)
                .minPriceVnd(BigDecimal.valueOf(70000))
                .avgRating(4.92)
                .build());

        embeddingListener.upsertLabEmbedding(LabProfileSyncPayload.builder()
                .labId(lab2Id)
                .slug("bw-classic-hanoi")
                .name("BW Classic Hà Nội")
                .fullAddress("Quận Hoàn Kiếm, Hà Nội")
                .provinceCode("01")
                .supportedFormats(List.of("FMT_135"))
                .supportedProcesses(List.of("BW"))
                .scannerModels(List.of("FRONTIER_SP3000"))
                .colorSignatureBio("Chuyên tráng đen trắng thủ công D-76 và Rodinal")
                .estimatedTurnaroundHours(36)
                .minPriceVnd(BigDecimal.valueOf(60000))
                .avgRating(4.75)
                .build());

        simulatedEsLabHits.add(LabSearchHitDto.builder()
                .labId(lab1Id.toString())
                .slug("llab-saigon-analog")
                .name("LLab Saigon Analog")
                .fullAddress("214/19 Nguyễn Văn Nguyễn, Quận 1, TP.HCM")
                .provinceCode("79")
                .supportedProcesses(List.of("C41", "ECN2", "BW"))
                .scannerModels(List.of("NORITSU_HS1800", "FRONTIER_SP3000"))
                .minPriceVnd(BigDecimal.valueOf(70000))
                .avgRating(4.92f)
                .build());

        HybridSearchResponse response = semanticSearchService.hybridSearch(HybridSearchQuery.builder()
                .query("lab tráng film điện ảnh ECN-2 ở Sài Gòn scan Noritsu màu trong trẻo")
                .scope(HybridSearchScope.LABS)
                .rrfK(60)
                .limit(5)
                .build());

        assertThat(response.labResults()).isNotEmpty();
        assertThat(response.labResults().getFirst().labId()).isEqualTo(lab1Id);
        assertThat(response.labResults().getFirst().finalRank()).isEqualTo(1);
        assertThat(response.labResults().getFirst().rrfFusionScore()).isEqualTo(0.03279); // 1/61 + 1/61
        assertThat(response.labResults().getFirst().rankDetails().esBm25Rank()).isEqualTo(1);
        assertThat(response.labResults().getFirst().rankDetails().pgvectorRank()).isEqualTo(1);
    }

    @Test
    void gracefulDegradationWhenElasticsearchBranchThrowsException() {
        UUID labId = UUID.randomUUID();
        embeddingListener.upsertLabEmbedding(LabProfileSyncPayload.builder()
                .labId(labId)
                .slug("cine-darkroom-saigon")
                .name("Cine Darkroom Sài Gòn")
                .fullAddress("Quận 3, TP.HCM")
                .supportedProcesses(List.of("ECN2", "C41"))
                .scannerModels(List.of("NORITSU_HS1800"))
                .colorSignatureBio("Chuyên tráng ECN-2 Kodak Vision3 500T")
                .minPriceVnd(BigDecimal.valueOf(80000))
                .avgRating(4.88)
                .build());

        simulateEsFailure.set(true);

        HybridSearchResponse response = semanticSearchService.hybridSearch(HybridSearchQuery.builder()
                .query("tráng ECN-2 Kodak Vision3 500T")
                .scope(HybridSearchScope.LABS)
                .rrfK(60)
                .limit(5)
                .build());

        assertThat(response.labResults()).hasSize(1);
        assertThat(response.labResults().getFirst().labId()).isEqualTo(labId);
        assertThat(response.labResults().getFirst().rankDetails().esBm25Rank()).isNull();
        assertThat(response.labResults().getFirst().rankDetails().pgvectorRank()).isEqualTo(1);
        assertThat(response.labResults().getFirst().rrfFusionScore()).isEqualTo(0.01639); // 1/61
    }

    private static double computeL2Norm(float[] vec) {
        double sum = 0.0;
        for (float v : vec) {
            sum += (double) v * v;
        }
        return Math.sqrt(sum);
    }

    @SuppressWarnings("unchecked")
    private LabEmbeddingRepository createLabEmbeddingRepositoryStub() {
        return (LabEmbeddingRepository) Proxy.newProxyInstance(
                LabEmbeddingRepository.class.getClassLoader(),
                new Class<?>[]{LabEmbeddingRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("save".equals(name)) {
                        LabEmbedding e = (LabEmbedding) args[0];
                        if (e.getId() == null) {
                            e.setId(UUID.randomUUID());
                        }
                        labEmbeddingStore.put(e.getLabId(), e);
                        return e;
                    }
                    if ("findByLabId".equals(name)) {
                        UUID labId = (UUID) args[0];
                        return Optional.ofNullable(labEmbeddingStore.get(labId));
                    }
                    if ("deleteByLabId".equals(name)) {
                        UUID labId = (UUID) args[0];
                        labEmbeddingStore.remove(labId);
                        return null;
                    }
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(labEmbeddingStore.values());
                    }
                    if ("findTopNearestLabsByCosineDistance".equals(name)) {
                        return List.of();
                    }
                    if ("setLocalHnswEfSearch".equals(name)) {
                        return null;
                    }
                    if ("toString".equals(name)) {
                        return "LabEmbeddingRepositoryStub";
                    }
                    return null;
                }
        );
    }

    @SuppressWarnings("unchecked")
    private ListingEmbeddingRepository createListingEmbeddingRepositoryStub() {
        return (ListingEmbeddingRepository) Proxy.newProxyInstance(
                ListingEmbeddingRepository.class.getClassLoader(),
                new Class<?>[]{ListingEmbeddingRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("save".equals(name)) {
                        ListingEmbedding e = (ListingEmbedding) args[0];
                        if (e.getId() == null) {
                            e.setId(UUID.randomUUID());
                        }
                        listingEmbeddingStore.put(e.getListingId(), e);
                        return e;
                    }
                    if ("findByListingId".equals(name)) {
                        UUID listingId = (UUID) args[0];
                        return Optional.ofNullable(listingEmbeddingStore.get(listingId));
                    }
                    if ("deleteByListingId".equals(name)) {
                        UUID listingId = (UUID) args[0];
                        listingEmbeddingStore.remove(listingId);
                        return null;
                    }
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(listingEmbeddingStore.values());
                    }
                    if ("findTopNearestListingsByCosineDistance".equals(name)) {
                        return List.of();
                    }
                    if ("setLocalHnswEfSearch".equals(name)) {
                        return null;
                    }
                    if ("toString".equals(name)) {
                        return "ListingEmbeddingRepositoryStub";
                    }
                    return null;
                }
        );
    }

    private SearchFacade createSearchFacadeStub() {
        return new SearchFacade() {
            @Override
            public void indexFilmLab(UUID labId) {
            }

            @Override
            public void indexMarketplaceListing(UUID listingId) {
            }

            @Override
            public void removeMarketplaceListing(UUID listingId) {
            }

            @Override
            public Object searchLabsNearby(double lat, double lon, double radiusKm) {
                return null;
            }

            @Override
            public Object searchListings(String keyword, String category) {
                return null;
            }

            @Override
            public LabSearchResponse searchLabsLexical(String query, String provinceCode, Double lat, Double lon, int limit) {
                if (simulateEsFailure.get()) {
                    throw new IllegalStateException("Simulated Elasticsearch cluster timeout");
                }
                return LabSearchResponse.builder()
                        .items(new ArrayList<>(simulatedEsLabHits))
                        .page(0)
                        .size(limit)
                        .totalElements(simulatedEsLabHits.size())
                        .totalPages(1)
                        .tookMs(5)
                        .build();
            }

            @Override
            public ListingSearchResponse searchListingsLexical(String query, String provinceCode, String category, int limit) {
                if (simulateEsFailure.get()) {
                    throw new IllegalStateException("Simulated Elasticsearch cluster timeout");
                }
                return ListingSearchResponse.builder()
                        .items(new ArrayList<>(simulatedEsListingHits))
                        .page(0)
                        .size(limit)
                        .totalElements(simulatedEsListingHits.size())
                        .totalPages(1)
                        .tookMs(5)
                        .build();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private KnowledgeChunkRepository createChunkRepositoryStub() {
        return (KnowledgeChunkRepository) Proxy.newProxyInstance(
                KnowledgeChunkRepository.class.getClassLoader(),
                new Class<?>[]{KnowledgeChunkRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findAll".equals(name) || "findTopNearestByCosineDistance".equals(name)) {
                        return List.<KnowledgeChunk>of();
                    }
                    return null;
                }
        );
    }

    @SuppressWarnings("unchecked")
    private AiUsageLogRepository createUsageLogRepositoryStub() {
        return (AiUsageLogRepository) Proxy.newProxyInstance(
                AiUsageLogRepository.class.getClassLoader(),
                new Class<?>[]{AiUsageLogRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        AiUsageLog entry = (AiUsageLog) args[0];
                        if (entry.getId() == null) {
                            entry.setId(UUID.randomUUID());
                        }
                        return entry;
                    }
                    return null;
                }
        );
    }
}
