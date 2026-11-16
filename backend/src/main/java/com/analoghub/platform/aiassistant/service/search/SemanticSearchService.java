package com.analoghub.platform.aiassistant.service.search;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.EmbeddingClient;
import com.analoghub.platform.aiassistant.client.EmbeddingTaskType;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.LabEmbedding;
import com.analoghub.platform.aiassistant.domain.ListingEmbedding;
import com.analoghub.platform.aiassistant.dto.HybridSearchResponse;
import com.analoghub.platform.aiassistant.dto.HybridSearchResponse.HybridKnowledgeSearchResultDto;
import com.analoghub.platform.aiassistant.dto.HybridSearchResponse.HybridLabSearchResultDto;
import com.analoghub.platform.aiassistant.dto.HybridSearchResponse.HybridListingSearchResultDto;
import com.analoghub.platform.aiassistant.dto.HybridSearchResponse.RankDetailsDto;
import com.analoghub.platform.aiassistant.dto.HybridSearchScope;
import com.analoghub.platform.aiassistant.listener.LabAndListingEmbeddingListener;
import com.analoghub.platform.aiassistant.repository.LabEmbeddingRepository;
import com.analoghub.platform.aiassistant.repository.ListingEmbeddingRepository;
import com.analoghub.platform.aiassistant.service.rag.RagRetrievalService;
import com.analoghub.platform.aiassistant.service.rag.RagRetrievalService.RetrievalResult;
import com.analoghub.platform.aiassistant.service.rag.RagRetrievalService.ScoredChunk;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.search.dto.LabSearchHitDto;
import com.analoghub.platform.search.dto.LabSearchResponse;
import com.analoghub.platform.search.dto.ListingSearchHitDto;
import com.analoghub.platform.search.dto.ListingSearchResponse;
import com.analoghub.platform.search.facade.SearchFacade;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Hybrid Semantic Search Service combining Elasticsearch BM25 (Lexical + Geo) and PostgreSQL pgvector HNSW
 * (Dense Semantic Vector) in parallel using Java 21 Virtual Threads and Reciprocal Rank Fusion (RRF k=60)
 * (Section 5 of 03-hybrid-recommender-and-semantic-search.md & API 7 of 15-aiassistant.md).
 */
@Slf4j
@Service
public class SemanticSearchService {

    public static final int DEFAULT_RRF_K = 60;
    public static final int MIN_RRF_K = 10;
    public static final int MAX_RRF_K = 120;
    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 30;
    public static final int CANDIDATE_POOL_SIZE = 50;
    public static final long BRANCH_TIMEOUT_MS = 800L;

    @Builder
    public record HybridSearchQuery(
            UUID userId,
            String query,
            HybridSearchScope scope,
            String provinceCode,
            Double lat,
            Double lon,
            Integer rrfK,
            Integer limit
    ) {
    }

    @Builder
    public record LexicalLabHit(
            UUID labId,
            Integer rank,
            Double bm25Score,
            String slug,
            String name,
            String fullAddress,
            String provinceCode,
            List<String> supportedProcesses,
            List<String> scannerModels,
            BigDecimal minPriceVnd,
            Double avgRating
    ) {
    }

    @Builder
    public record VectorLabHit(
            UUID labId,
            Integer rank,
            Double cosineSimilarity,
            String slug,
            String name,
            String fullAddress,
            String provinceCode,
            List<String> supportedProcesses,
            List<String> scannerModels,
            BigDecimal minPriceVnd,
            Double avgRating
    ) {
    }

    @Builder
    public record LexicalListingHit(
            UUID listingId,
            Integer rank,
            Double bm25Score,
            String slug,
            String title,
            String category,
            String brand,
            String modelName,
            String conditionGrade,
            BigDecimal priceVnd,
            String provinceCode
    ) {
    }

    @Builder
    public record VectorListingHit(
            UUID listingId,
            Integer rank,
            Double cosineSimilarity,
            String slug,
            String title,
            String category,
            String brand,
            String modelName,
            String conditionGrade,
            BigDecimal priceVnd,
            String provinceCode
    ) {
    }

    private final SearchFacade searchFacade;
    private final LabEmbeddingRepository labEmbeddingRepository;
    private final ListingEmbeddingRepository listingEmbeddingRepository;
    private final LabAndListingEmbeddingListener embeddingListener;
    private final RagRetrievalService ragRetrievalService;
    private final AiModelRouter aiModelRouter;
    private final AiProperties aiProperties;

    private final Map<String, float[]> queryEmbeddingCache = new ConcurrentHashMap<>();

    @Autowired
    public SemanticSearchService(
            SearchFacade searchFacade,
            LabEmbeddingRepository labEmbeddingRepository,
            ListingEmbeddingRepository listingEmbeddingRepository,
            LabAndListingEmbeddingListener embeddingListener,
            RagRetrievalService ragRetrievalService,
            AiModelRouter aiModelRouter,
            AiProperties aiProperties
    ) {
        this.searchFacade = searchFacade;
        this.labEmbeddingRepository = labEmbeddingRepository;
        this.listingEmbeddingRepository = listingEmbeddingRepository;
        this.embeddingListener = embeddingListener;
        this.ragRetrievalService = ragRetrievalService;
        this.aiModelRouter = aiModelRouter;
        this.aiProperties = aiProperties;
    }

    public HybridSearchResponse hybridSearch(HybridSearchQuery request) {
        long startNanos = System.nanoTime();
        validateQuery(request);

        String queryText = request.query().trim();
        HybridSearchScope scope = request.scope() != null ? request.scope() : HybridSearchScope.ALL;
        int rrfK = request.rrfK() != null ? request.rrfK() : DEFAULT_RRF_K;
        int limit = request.limit() != null ? request.limit() : DEFAULT_LIMIT;
        String provinceCode = StringUtils.hasText(request.provinceCode())
                ? request.provinceCode().trim()
                : null;

        float[] queryVector = embedQueryWithCache(request.userId(), queryText);

        List<HybridLabSearchResultDto> labResults = List.of();
        List<HybridListingSearchResultDto> listingResults = List.of();
        List<HybridKnowledgeSearchResultDto> knowledgeResults = List.of();

        try (ExecutorService vThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<List<HybridLabSearchResultDto>> labsFuture =
                    (scope == HybridSearchScope.ALL || scope == HybridSearchScope.LABS)
                            ? CompletableFuture.supplyAsync(
                                    () -> executeParallelLabHybridSearch(
                                            queryText, queryVector, provinceCode, request.lat(), request.lon(), rrfK, limit),
                                    vThreadExecutor)
                            : CompletableFuture.completedFuture(List.of());

            CompletableFuture<List<HybridListingSearchResultDto>> listingsFuture =
                    (scope == HybridSearchScope.ALL || scope == HybridSearchScope.LISTINGS)
                            ? CompletableFuture.supplyAsync(
                                    () -> executeParallelListingHybridSearch(
                                            queryText, queryVector, provinceCode, rrfK, limit),
                                    vThreadExecutor)
                            : CompletableFuture.completedFuture(List.of());

            CompletableFuture<List<HybridKnowledgeSearchResultDto>> knowledgeFuture =
                    (scope == HybridSearchScope.ALL || scope == HybridSearchScope.KNOWLEDGE)
                            ? CompletableFuture.supplyAsync(
                                    () -> executeKnowledgeSemanticSearch(request.userId(), queryText, limit),
                                    vThreadExecutor)
                            : CompletableFuture.completedFuture(List.of());

            labResults = labsFuture.join();
            listingResults = listingsFuture.join();
            knowledgeResults = knowledgeFuture.join();
        }

        long latencyMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);

        return HybridSearchResponse.builder()
                .query(queryText)
                .scope(scope.name())
                .rrfK(rrfK)
                .embeddingModel(resolveEmbeddingModelName())
                .latencyMs(latencyMs)
                .labResults(labResults)
                .listingResults(listingResults)
                .knowledgeResults(knowledgeResults)
                .build();
    }

    /**
     * Executes Elasticsearch BM25 and pgvector HNSW branches in parallel on Java 21 Virtual Threads
     * and merges rankings via RRF (Section 5.1 & 5.2 of 03-hybrid-recommender-and-semantic-search.md).
     */
    public List<HybridLabSearchResultDto> executeParallelLabHybridSearch(
            String queryText,
            float[] queryVector,
            String provinceCode,
            Double lat,
            Double lon,
            int rrfK,
            int limit
    ) {
        try (ExecutorService vThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<List<LexicalLabHit>> esFuture = CompletableFuture
                    .supplyAsync(() -> fetchLexicalLabsFromEs(queryText, provinceCode, lat, lon, CANDIDATE_POOL_SIZE), vThreadExecutor)
                    .orTimeout(BRANCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("Elasticsearch lab lexical branch failed/timed out, degrading gracefully: {}", ex.getMessage());
                        return List.of();
                    });

            CompletableFuture<List<VectorLabHit>> vecFuture = CompletableFuture
                    .supplyAsync(() -> fetchDenseVectorLabs(queryText, queryVector, provinceCode, CANDIDATE_POOL_SIZE), vThreadExecutor)
                    .orTimeout(BRANCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("pgvector lab semantic branch failed/timed out, degrading gracefully: {}", ex.getMessage());
                        return List.of();
                    });

            List<LexicalLabHit> esHits = esFuture.join();
            List<VectorLabHit> vecHits = vecFuture.join();

            return mergeLabsRrf(esHits, vecHits, rrfK, limit);
        }
    }

    public List<HybridListingSearchResultDto> executeParallelListingHybridSearch(
            String queryText,
            float[] queryVector,
            String provinceCode,
            int rrfK,
            int limit
    ) {
        try (ExecutorService vThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<List<LexicalListingHit>> esFuture = CompletableFuture
                    .supplyAsync(() -> fetchLexicalListingsFromEs(queryText, provinceCode, CANDIDATE_POOL_SIZE), vThreadExecutor)
                    .orTimeout(BRANCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("Elasticsearch listing lexical branch failed/timed out, degrading gracefully: {}", ex.getMessage());
                        return List.of();
                    });

            CompletableFuture<List<VectorListingHit>> vecFuture = CompletableFuture
                    .supplyAsync(() -> fetchDenseVectorListings(queryText, queryVector, provinceCode, CANDIDATE_POOL_SIZE), vThreadExecutor)
                    .orTimeout(BRANCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("pgvector listing semantic branch failed/timed out, degrading gracefully: {}", ex.getMessage());
                        return List.of();
                    });

            List<LexicalListingHit> esHits = esFuture.join();
            List<VectorListingHit> vecHits = vecFuture.join();

            return mergeListingsRrf(esHits, vecHits, rrfK, limit);
        }
    }

    /**
     * Reciprocal Rank Fusion (RRF) for Film Labs (Section 5.1 & 5.3):
     * RRF_Score(d) = (1 / (k + rank_ES(d))) + (1 / (k + rank_vec(d)))
     */
    public List<HybridLabSearchResultDto> mergeLabsRrf(
            List<LexicalLabHit> esHits,
            List<VectorLabHit> vecHits,
            int rrfK,
            int limit
    ) {
        int safeK = rrfK > 0 ? rrfK : DEFAULT_RRF_K;
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;

        Map<UUID, LexicalLabHit> esMap = new LinkedHashMap<>();
        if (esHits != null) {
            for (int i = 0; i < esHits.size(); i++) {
                LexicalLabHit hit = esHits.get(i);
                if (hit != null && hit.labId() != null && !esMap.containsKey(hit.labId())) {
                    int rank = hit.rank() != null && hit.rank() > 0 ? hit.rank() : (i + 1);
                    esMap.put(hit.labId(), LexicalLabHit.builder()
                            .labId(hit.labId())
                            .rank(rank)
                            .bm25Score(hit.bm25Score() != null ? hit.bm25Score() : Math.max(1.0, 20.0 - i))
                            .slug(hit.slug())
                            .name(hit.name())
                            .fullAddress(hit.fullAddress())
                            .provinceCode(hit.provinceCode())
                            .supportedProcesses(hit.supportedProcesses())
                            .scannerModels(hit.scannerModels())
                            .minPriceVnd(hit.minPriceVnd())
                            .avgRating(hit.avgRating())
                            .build());
                }
            }
        }

        Map<UUID, VectorLabHit> vecMap = new LinkedHashMap<>();
        if (vecHits != null) {
            for (int i = 0; i < vecHits.size(); i++) {
                VectorLabHit hit = vecHits.get(i);
                if (hit != null && hit.labId() != null && !vecMap.containsKey(hit.labId())) {
                    int rank = hit.rank() != null && hit.rank() > 0 ? hit.rank() : (i + 1);
                    vecMap.put(hit.labId(), VectorLabHit.builder()
                            .labId(hit.labId())
                            .rank(rank)
                            .cosineSimilarity(hit.cosineSimilarity() != null ? hit.cosineSimilarity() : 0.0)
                            .slug(hit.slug())
                            .name(hit.name())
                            .fullAddress(hit.fullAddress())
                            .provinceCode(hit.provinceCode())
                            .supportedProcesses(hit.supportedProcesses())
                            .scannerModels(hit.scannerModels())
                            .minPriceVnd(hit.minPriceVnd())
                            .avgRating(hit.avgRating())
                            .build());
                }
            }
        }

        Set<UUID> allLabIds = new LinkedHashSet<>();
        allLabIds.addAll(esMap.keySet());
        allLabIds.addAll(vecMap.keySet());

        if (allLabIds.isEmpty()) {
            return List.of();
        }

        record ScoredLabCandidate(
                UUID labId,
                double rawRrfScore,
                double roundedRrfScore,
                LexicalLabHit esHit,
                VectorLabHit vecHit
        ) {}

        List<ScoredLabCandidate> candidates = new ArrayList<>(allLabIds.size());
        for (UUID labId : allLabIds) {
            LexicalLabHit es = esMap.get(labId);
            VectorLabHit vec = vecMap.get(labId);

            double esContribution = es != null ? (1.0 / (safeK + es.rank())) : 0.0;
            double vecContribution = vec != null ? (1.0 / (safeK + vec.rank())) : 0.0;
            double rawRrf = esContribution + vecContribution;
            double roundedRrf = round5(rawRrf);

            candidates.add(new ScoredLabCandidate(labId, rawRrf, roundedRrf, es, vec));
        }

        candidates.sort((a, b) -> {
            int cmp = Double.compare(b.rawRrfScore(), a.rawRrfScore());
            if (cmp != 0) {
                return cmp;
            }
            double simA = a.vecHit() != null && a.vecHit().cosineSimilarity() != null ? a.vecHit().cosineSimilarity() : 0.0;
            double simB = b.vecHit() != null && b.vecHit().cosineSimilarity() != null ? b.vecHit().cosineSimilarity() : 0.0;
            return Double.compare(simB, simA);
        });

        int count = Math.min(safeLimit, candidates.size());
        List<HybridLabSearchResultDto> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ScoredLabCandidate c = candidates.get(i);
            LexicalLabHit es = c.esHit();
            VectorLabHit vec = c.vecHit();

            String slug = es != null && StringUtils.hasText(es.slug()) ? es.slug() : (vec != null ? vec.slug() : null);
            String name = es != null && StringUtils.hasText(es.name()) ? es.name() : (vec != null ? vec.name() : null);
            String address = es != null && StringUtils.hasText(es.fullAddress())
                    ? es.fullAddress()
                    : (vec != null ? vec.fullAddress() : null);
            List<String> processes = es != null && es.supportedProcesses() != null && !es.supportedProcesses().isEmpty()
                    ? es.supportedProcesses()
                    : (vec != null && vec.supportedProcesses() != null ? vec.supportedProcesses() : List.of());
            List<String> scanners = es != null && es.scannerModels() != null && !es.scannerModels().isEmpty()
                    ? es.scannerModels()
                    : (vec != null && vec.scannerModels() != null ? vec.scannerModels() : List.of());
            BigDecimal minPrice = es != null && es.minPriceVnd() != null
                    ? es.minPriceVnd()
                    : (vec != null ? vec.minPriceVnd() : null);
            Double avgRating = es != null && es.avgRating() != null
                    ? es.avgRating()
                    : (vec != null ? vec.avgRating() : null);

            results.add(HybridLabSearchResultDto.builder()
                    .finalRank(i + 1)
                    .labId(c.labId())
                    .slug(slug)
                    .name(name)
                    .fullAddress(address)
                    .supportedProcesses(processes)
                    .scannerModels(scanners)
                    .minPriceVnd(minPrice)
                    .avgRating(avgRating)
                    .rrfFusionScore(c.roundedRrfScore())
                    .rankDetails(RankDetailsDto.builder()
                            .esBm25Rank(es != null ? es.rank() : null)
                            .esBm25Score(es != null ? round3Nullable(es.bm25Score()) : null)
                            .pgvectorRank(vec != null ? vec.rank() : null)
                            .cosineSimilarity(vec != null ? round3Nullable(vec.cosineSimilarity()) : null)
                            .build())
                    .build());
        }

        return List.copyOf(results);
    }

    /**
     * Reciprocal Rank Fusion (RRF) for Marketplace Listings (Section 5.1).
     */
    public List<HybridListingSearchResultDto> mergeListingsRrf(
            List<LexicalListingHit> esHits,
            List<VectorListingHit> vecHits,
            int rrfK,
            int limit
    ) {
        int safeK = rrfK > 0 ? rrfK : DEFAULT_RRF_K;
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;

        Map<UUID, LexicalListingHit> esMap = new LinkedHashMap<>();
        if (esHits != null) {
            for (int i = 0; i < esHits.size(); i++) {
                LexicalListingHit hit = esHits.get(i);
                if (hit != null && hit.listingId() != null && !esMap.containsKey(hit.listingId())) {
                    int rank = hit.rank() != null && hit.rank() > 0 ? hit.rank() : (i + 1);
                    esMap.put(hit.listingId(), LexicalListingHit.builder()
                            .listingId(hit.listingId())
                            .rank(rank)
                            .bm25Score(hit.bm25Score() != null ? hit.bm25Score() : Math.max(1.0, 20.0 - i))
                            .slug(hit.slug())
                            .title(hit.title())
                            .category(hit.category())
                            .brand(hit.brand())
                            .modelName(hit.modelName())
                            .conditionGrade(hit.conditionGrade())
                            .priceVnd(hit.priceVnd())
                            .provinceCode(hit.provinceCode())
                            .build());
                }
            }
        }

        Map<UUID, VectorListingHit> vecMap = new LinkedHashMap<>();
        if (vecHits != null) {
            for (int i = 0; i < vecHits.size(); i++) {
                VectorListingHit hit = vecHits.get(i);
                if (hit != null && hit.listingId() != null && !vecMap.containsKey(hit.listingId())) {
                    int rank = hit.rank() != null && hit.rank() > 0 ? hit.rank() : (i + 1);
                    vecMap.put(hit.listingId(), VectorListingHit.builder()
                            .listingId(hit.listingId())
                            .rank(rank)
                            .cosineSimilarity(hit.cosineSimilarity() != null ? hit.cosineSimilarity() : 0.0)
                            .slug(hit.slug())
                            .title(hit.title())
                            .category(hit.category())
                            .brand(hit.brand())
                            .modelName(hit.modelName())
                            .conditionGrade(hit.conditionGrade())
                            .priceVnd(hit.priceVnd())
                            .provinceCode(hit.provinceCode())
                            .build());
                }
            }
        }

        Set<UUID> allListingIds = new LinkedHashSet<>();
        allListingIds.addAll(esMap.keySet());
        allListingIds.addAll(vecMap.keySet());

        if (allListingIds.isEmpty()) {
            return List.of();
        }

        record ScoredListingCandidate(
                UUID listingId,
                double rawRrfScore,
                double roundedRrfScore,
                LexicalListingHit esHit,
                VectorListingHit vecHit
        ) {}

        List<ScoredListingCandidate> candidates = new ArrayList<>(allListingIds.size());
        for (UUID listingId : allListingIds) {
            LexicalListingHit es = esMap.get(listingId);
            VectorListingHit vec = vecMap.get(listingId);

            double esContribution = es != null ? (1.0 / (safeK + es.rank())) : 0.0;
            double vecContribution = vec != null ? (1.0 / (safeK + vec.rank())) : 0.0;
            double rawRrf = esContribution + vecContribution;

            candidates.add(new ScoredListingCandidate(listingId, rawRrf, round5(rawRrf), es, vec));
        }

        candidates.sort((a, b) -> {
            int cmp = Double.compare(b.rawRrfScore(), a.rawRrfScore());
            if (cmp != 0) {
                return cmp;
            }
            double simA = a.vecHit() != null && a.vecHit().cosineSimilarity() != null ? a.vecHit().cosineSimilarity() : 0.0;
            double simB = b.vecHit() != null && b.vecHit().cosineSimilarity() != null ? b.vecHit().cosineSimilarity() : 0.0;
            return Double.compare(simB, simA);
        });

        int count = Math.min(safeLimit, candidates.size());
        List<HybridListingSearchResultDto> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ScoredListingCandidate c = candidates.get(i);
            LexicalListingHit es = c.esHit();
            VectorListingHit vec = c.vecHit();

            results.add(HybridListingSearchResultDto.builder()
                    .finalRank(i + 1)
                    .listingId(c.listingId())
                    .slug(es != null && StringUtils.hasText(es.slug()) ? es.slug() : (vec != null ? vec.slug() : null))
                    .title(es != null && StringUtils.hasText(es.title()) ? es.title() : (vec != null ? vec.title() : null))
                    .category(es != null && StringUtils.hasText(es.category()) ? es.category() : (vec != null ? vec.category() : null))
                    .brand(es != null && StringUtils.hasText(es.brand()) ? es.brand() : (vec != null ? vec.brand() : null))
                    .modelName(es != null && StringUtils.hasText(es.modelName()) ? es.modelName() : (vec != null ? vec.modelName() : null))
                    .conditionGrade(es != null && StringUtils.hasText(es.conditionGrade())
                            ? es.conditionGrade()
                            : (vec != null ? vec.conditionGrade() : null))
                    .priceVnd(es != null && es.priceVnd() != null ? es.priceVnd() : (vec != null ? vec.priceVnd() : null))
                    .provinceCode(es != null && StringUtils.hasText(es.provinceCode())
                            ? es.provinceCode()
                            : (vec != null ? vec.provinceCode() : null))
                    .rrfFusionScore(c.roundedRrfScore())
                    .rankDetails(RankDetailsDto.builder()
                            .esBm25Rank(es != null ? es.rank() : null)
                            .esBm25Score(es != null ? round3Nullable(es.bm25Score()) : null)
                            .pgvectorRank(vec != null ? vec.rank() : null)
                            .cosineSimilarity(vec != null ? round3Nullable(vec.cosineSimilarity()) : null)
                            .build())
                    .build());
        }

        return List.copyOf(results);
    }

    private List<LexicalLabHit> fetchLexicalLabsFromEs(
            String queryText,
            String provinceCode,
            Double lat,
            Double lon,
            int limit
    ) {
        if (searchFacade == null) {
            return List.of();
        }
        LabSearchResponse esResponse = searchFacade.searchLabsLexical(queryText, provinceCode, lat, lon, limit);
        if (esResponse == null || esResponse.items() == null || esResponse.items().isEmpty()) {
            return List.of();
        }
        List<LexicalLabHit> hits = new ArrayList<>(esResponse.items().size());
        for (int i = 0; i < esResponse.items().size(); i++) {
            LabSearchHitDto item = esResponse.items().get(i);
            UUID labId = parseUuidSafely(item.labId());
            if (labId == null) {
                continue;
            }
            hits.add(LexicalLabHit.builder()
                    .labId(labId)
                    .rank(i + 1)
                    .bm25Score(Math.max(1.0, 20.0 - (i * 0.5)))
                    .slug(item.slug())
                    .name(item.name())
                    .fullAddress(item.fullAddress())
                    .provinceCode(item.provinceCode())
                    .supportedProcesses(item.supportedProcesses())
                    .scannerModels(item.scannerModels())
                    .minPriceVnd(item.minPriceVnd())
                    .avgRating(item.avgRating() != null ? item.avgRating().doubleValue() : null)
                    .build());
        }
        return hits;
    }

    private List<LexicalListingHit> fetchLexicalListingsFromEs(
            String queryText,
            String provinceCode,
            int limit
    ) {
        if (searchFacade == null) {
            return List.of();
        }
        ListingSearchResponse esResponse = searchFacade.searchListingsLexical(queryText, provinceCode, null, limit);
        if (esResponse == null || esResponse.items() == null || esResponse.items().isEmpty()) {
            return List.of();
        }
        List<LexicalListingHit> hits = new ArrayList<>(esResponse.items().size());
        for (int i = 0; i < esResponse.items().size(); i++) {
            ListingSearchHitDto item = esResponse.items().get(i);
            UUID listingId = parseUuidSafely(item.listingId());
            if (listingId == null) {
                continue;
            }
            hits.add(LexicalListingHit.builder()
                    .listingId(listingId)
                    .rank(i + 1)
                    .bm25Score(item.score() != null ? item.score().doubleValue() : Math.max(1.0, 20.0 - (i * 0.5)))
                    .slug(item.slug())
                    .title(item.title())
                    .category(item.category())
                    .brand(item.brand())
                    .modelName(item.modelName())
                    .conditionGrade(item.conditionGrade())
                    .priceVnd(item.priceVnd())
                    .provinceCode(item.provinceCode())
                    .build());
        }
        return hits;
    }

    private List<VectorLabHit> fetchDenseVectorLabs(
            String queryText,
            float[] queryVector,
            String provinceCode,
            int limit
    ) {
        try {
            labEmbeddingRepository.setLocalHnswEfSearch();
        } catch (Exception ignored) {
            // Non-Postgres or read-only context
        }

        try {
            String vectorLiteral = toPgVectorLiteral(queryVector);
            List<Map<String, Object>> rows = labEmbeddingRepository.findTopNearestLabsByCosineDistance(vectorLiteral, limit);
            if (rows != null && !rows.isEmpty()) {
                List<VectorLabHit> hits = new ArrayList<>();
                int rank = 1;
                for (Map<String, Object> row : rows) {
                    UUID labId = parseUuidObject(row.get("lab_id"));
                    if (labId == null) {
                        continue;
                    }
                    LabEmbedding cached = embeddingListener != null
                            ? embeddingListener.getCachedLabEmbedding(labId).orElse(null)
                            : null;
                    if (provinceCode != null && cached != null && StringUtils.hasText(cached.getProvinceCode())
                            && !provinceCode.equalsIgnoreCase(cached.getProvinceCode())) {
                        continue;
                    }
                    double sim = parseDoubleObject(row.get("cosine_similarity"), 0.75);
                    hits.add(VectorLabHit.builder()
                            .labId(labId)
                            .rank(rank++)
                            .cosineSimilarity(sim)
                            .slug(cached != null ? cached.getSlug() : null)
                            .name(cached != null ? cached.getLabName() : null)
                            .fullAddress(cached != null ? cached.getFullAddress() : null)
                            .provinceCode(cached != null ? cached.getProvinceCode() : null)
                            .supportedProcesses(cached != null ? cached.getSupportedProcesses() : List.of())
                            .scannerModels(cached != null ? cached.getScannerModels() : List.of())
                            .minPriceVnd(cached != null ? cached.getMinPriceVnd() : null)
                            .avgRating(cached != null ? cached.getAvgRating() : null)
                            .build());
                }
                if (!hits.isEmpty()) {
                    return hits;
                }
            }
        } catch (Exception ex) {
            log.debug("Native pgvector query on ai_lab_embeddings unavailable, using in-memory vector scoring: {}", ex.getMessage());
        }

        Map<UUID, LabEmbedding> allLabs = new LinkedHashMap<>();
        try {
            List<LabEmbedding> repoLabs = labEmbeddingRepository.findAll();
            if (repoLabs != null) {
                for (LabEmbedding le : repoLabs) {
                    if (le != null && le.getLabId() != null) {
                        allLabs.put(le.getLabId(), le);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (embeddingListener != null) {
            Collection<LabEmbedding> cachedLabs = embeddingListener.getAllCachedLabEmbeddings();
            for (LabEmbedding le : cachedLabs) {
                if (le != null && le.getLabId() != null) {
                    allLabs.put(le.getLabId(), le);
                }
            }
        }

        if (allLabs.isEmpty()) {
            return List.of();
        }

        List<VectorLabHit> scored = new ArrayList<>();
        for (LabEmbedding le : allLabs.values()) {
            if (provinceCode != null && StringUtils.hasText(le.getProvinceCode())
                    && !provinceCode.equalsIgnoreCase(le.getProvinceCode())) {
                continue;
            }
            float[] emb = le.getEmbedding();
            if (emb == null && StringUtils.hasText(le.getDescription())) {
                emb = aiModelRouter.embed(null, le.getDescription(), EmbeddingTaskType.RETRIEVAL_DOCUMENT);
            }
            double cosine = cosineSimilarity(queryVector, emb);
            double lexical = computeSimpleLexicalOverlap(queryText, le.getDescription());
            double combinedSim = Math.min(1.0, Math.max(0.0, (0.45 * Math.max(0.0, cosine)) + (0.55 * lexical)));

            scored.add(VectorLabHit.builder()
                    .labId(le.getLabId())
                    .cosineSimilarity(round3(combinedSim))
                    .slug(le.getSlug())
                    .name(le.getLabName())
                    .fullAddress(le.getFullAddress())
                    .provinceCode(le.getProvinceCode())
                    .supportedProcesses(le.getSupportedProcesses())
                    .scannerModels(le.getScannerModels())
                    .minPriceVnd(le.getMinPriceVnd())
                    .avgRating(le.getAvgRating())
                    .build());
        }

        scored.sort(Comparator.comparingDouble(VectorLabHit::cosineSimilarity).reversed());
        List<VectorLabHit> ranked = new ArrayList<>(Math.min(limit, scored.size()));
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            VectorLabHit item = scored.get(i);
            ranked.add(VectorLabHit.builder()
                    .labId(item.labId())
                    .rank(i + 1)
                    .cosineSimilarity(item.cosineSimilarity())
                    .slug(item.slug())
                    .name(item.name())
                    .fullAddress(item.fullAddress())
                    .provinceCode(item.provinceCode())
                    .supportedProcesses(item.supportedProcesses())
                    .scannerModels(item.scannerModels())
                    .minPriceVnd(item.minPriceVnd())
                    .avgRating(item.avgRating())
                    .build());
        }
        return ranked;
    }

    private List<VectorListingHit> fetchDenseVectorListings(
            String queryText,
            float[] queryVector,
            String provinceCode,
            int limit
    ) {
        try {
            listingEmbeddingRepository.setLocalHnswEfSearch();
        } catch (Exception ignored) {
        }

        try {
            String vectorLiteral = toPgVectorLiteral(queryVector);
            List<Map<String, Object>> rows = listingEmbeddingRepository.findTopNearestListingsByCosineDistance(vectorLiteral, limit);
            if (rows != null && !rows.isEmpty()) {
                List<VectorListingHit> hits = new ArrayList<>();
                int rank = 1;
                for (Map<String, Object> row : rows) {
                    UUID listingId = parseUuidObject(row.get("listing_id"));
                    if (listingId == null) {
                        continue;
                    }
                    ListingEmbedding cached = embeddingListener != null
                            ? embeddingListener.getCachedListingEmbedding(listingId).orElse(null)
                            : null;
                    if (provinceCode != null && cached != null && StringUtils.hasText(cached.getProvinceCode())
                            && !provinceCode.equalsIgnoreCase(cached.getProvinceCode())) {
                        continue;
                    }
                    double sim = parseDoubleObject(row.get("cosine_similarity"), 0.75);
                    hits.add(VectorListingHit.builder()
                            .listingId(listingId)
                            .rank(rank++)
                            .cosineSimilarity(sim)
                            .slug(cached != null ? cached.getSlug() : null)
                            .title(cached != null ? cached.getTitle() : null)
                            .category(cached != null ? cached.getCategory() : null)
                            .brand(cached != null ? cached.getBrand() : null)
                            .modelName(cached != null ? cached.getModelName() : null)
                            .conditionGrade(cached != null ? cached.getConditionGrade() : null)
                            .priceVnd(cached != null ? cached.getPriceVnd() : null)
                            .provinceCode(cached != null ? cached.getProvinceCode() : null)
                            .build());
                }
                if (!hits.isEmpty()) {
                    return hits;
                }
            }
        } catch (Exception ex) {
            log.debug("Native pgvector query on ai_listing_embeddings unavailable, using in-memory vector scoring: {}", ex.getMessage());
        }

        Map<UUID, ListingEmbedding> allListings = new LinkedHashMap<>();
        try {
            List<ListingEmbedding> repoListings = listingEmbeddingRepository.findAll();
            if (repoListings != null) {
                for (ListingEmbedding le : repoListings) {
                    if (le != null && le.getListingId() != null) {
                        allListings.put(le.getListingId(), le);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (embeddingListener != null) {
            Collection<ListingEmbedding> cachedListings = embeddingListener.getAllCachedListingEmbeddings();
            for (ListingEmbedding le : cachedListings) {
                if (le != null && le.getListingId() != null) {
                    allListings.put(le.getListingId(), le);
                }
            }
        }

        if (allListings.isEmpty()) {
            return List.of();
        }

        List<VectorListingHit> scored = new ArrayList<>();
        for (ListingEmbedding le : allListings.values()) {
            if (provinceCode != null && StringUtils.hasText(le.getProvinceCode())
                    && !provinceCode.equalsIgnoreCase(le.getProvinceCode())) {
                continue;
            }
            float[] emb = le.getEmbedding();
            if (emb == null && StringUtils.hasText(le.getFeatures())) {
                emb = aiModelRouter.embed(null, le.getFeatures(), EmbeddingTaskType.RETRIEVAL_DOCUMENT);
            }
            double cosine = cosineSimilarity(queryVector, emb);
            double lexical = computeSimpleLexicalOverlap(queryText, le.getFeatures());
            double combinedSim = Math.min(1.0, Math.max(0.0, (0.45 * Math.max(0.0, cosine)) + (0.55 * lexical)));

            scored.add(VectorListingHit.builder()
                    .listingId(le.getListingId())
                    .cosineSimilarity(round3(combinedSim))
                    .slug(le.getSlug())
                    .title(le.getTitle())
                    .category(le.getCategory())
                    .brand(le.getBrand())
                    .modelName(le.getModelName())
                    .conditionGrade(le.getConditionGrade())
                    .priceVnd(le.getPriceVnd())
                    .provinceCode(le.getProvinceCode())
                    .build());
        }

        scored.sort(Comparator.comparingDouble(VectorListingHit::cosineSimilarity).reversed());
        List<VectorListingHit> ranked = new ArrayList<>(Math.min(limit, scored.size()));
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            VectorListingHit item = scored.get(i);
            ranked.add(VectorListingHit.builder()
                    .listingId(item.listingId())
                    .rank(i + 1)
                    .cosineSimilarity(item.cosineSimilarity())
                    .slug(item.slug())
                    .title(item.title())
                    .category(item.category())
                    .brand(item.brand())
                    .modelName(item.modelName())
                    .conditionGrade(item.conditionGrade())
                    .priceVnd(item.priceVnd())
                    .provinceCode(item.provinceCode())
                    .build());
        }
        return ranked;
    }

    private List<HybridKnowledgeSearchResultDto> executeKnowledgeSemanticSearch(
            UUID userId,
            String queryText,
            int limit
    ) {
        if (ragRetrievalService == null) {
            return List.of();
        }
        try {
            RetrievalResult result = ragRetrievalService.retrieve(userId, queryText, null, null, limit);
            if (result == null || result.abstained() || result.selectedChunks() == null) {
                return List.of();
            }
            List<HybridKnowledgeSearchResultDto> list = new ArrayList<>(result.selectedChunks().size());
            for (int i = 0; i < result.selectedChunks().size(); i++) {
                ScoredChunk c = result.selectedChunks().get(i);
                String snippet = c.chunkContent() != null && c.chunkContent().length() > 200
                        ? c.chunkContent().substring(0, 200) + "..."
                        : c.chunkContent();
                list.add(HybridKnowledgeSearchResultDto.builder()
                        .finalRank(i + 1)
                        .chunkId(c.chunkId())
                        .sourceType(c.sourceType())
                        .articleSlug(c.articleSlug())
                        .chunkTitle(c.chunkTitle())
                        .snippet(snippet)
                        .similarityScore(c.similarityScore())
                        .rerankScore(c.rerankScore())
                        .build());
            }
            return list;
        } catch (Exception ex) {
            log.warn("Knowledge semantic search branch failed gracefully: {}", ex.getMessage());
            return List.of();
        }
    }

    private float[] embedQueryWithCache(UUID userId, String queryText) {
        String sha256 = sha256Hex(queryText.toLowerCase(Locale.ROOT));
        float[] cached = queryEmbeddingCache.get(sha256);
        if (cached != null) {
            return cached;
        }
        try {
            float[] vector = aiModelRouter.embed(userId, queryText, EmbeddingTaskType.RETRIEVAL_QUERY);
            queryEmbeddingCache.put(sha256, vector);
            return vector;
        } catch (Exception ex) {
            log.warn("Embedding provider failed during hybrid search, falling back to deterministic vector: {}", ex.getMessage());
            return EmbeddingClient.deterministicEmbedding(queryText, EmbeddingTaskType.RETRIEVAL_QUERY, EmbeddingClient.REQUIRED_DIMENSIONS);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input != null ? input : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            return String.valueOf(input != null ? input.hashCode() : 0);
        }
    }

    private static void validateQuery(HybridSearchQuery request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Câu truy vấn tìm kiếm (q) không được để trống.");
        }
        String trimmed = request.query().trim();
        if (trimmed.length() < 2 || trimmed.length() > 300) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Câu truy vấn tìm kiếm phải có độ dài từ 2 đến 300 ký tự.");
        }
        if (request.rrfK() != null && (request.rrfK() < MIN_RRF_K || request.rrfK() > MAX_RRF_K)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Hằng số rrfK phải nằm trong khoảng từ 10 đến 120.");
        }
        if (request.limit() != null && (request.limit() < 1 || request.limit() > MAX_LIMIT)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số limit phải nằm trong khoảng từ 1 đến 30.");
        }
    }

    private String resolveEmbeddingModelName() {
        ModelProvider provider = aiProperties.resolveEmbeddingProvider();
        if (provider == ModelProvider.OPENAI) {
            String m = aiProperties.getOpenai().getEmbeddingModel();
            return StringUtils.hasText(m) ? m : "text-embedding-3-small";
        }
        String m = aiProperties.getGemini().getEmbeddingModel();
        return StringUtils.hasText(m) ? m : "text-embedding-004";
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA <= 1e-12 || normB <= 1e-12) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    static double computeSimpleLexicalOverlap(String query, String documentText) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(documentText)) {
            return 0.0;
        }
        String foldedQuery = foldVietnamese(query);
        String foldedDoc = foldVietnamese(documentText);
        String[] tokens = foldedQuery.split("[^a-z0-9\\-]+");
        int total = 0;
        int matched = 0;
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            total++;
            if (foldedDoc.contains(token)) {
                matched++;
            }
        }
        if (total == 0) {
            return 0.0;
        }
        return Math.min(1.0, ((double) matched / total) * 0.90 + (matched >= 2 ? 0.10 : 0.0));
    }

    private static String foldVietnamese(String text) {
        if (text == null) {
            return "";
        }
        String replaced = text.replace('đ', 'd').replace('Đ', 'D');
        String decomposed = Normalizer.normalize(replaced, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static String toPgVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder(vector.length * 10);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.6f", vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    private static UUID parseUuidSafely(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static UUID parseUuidObject(Object val) {
        if (val instanceof UUID u) {
            return u;
        }
        if (val instanceof String s) {
            return parseUuidSafely(s);
        }
        return null;
    }

    private static double parseDoubleObject(Object val, double fallback) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static double round5(double val) {
        return Math.round(val * 100000.0) / 100000.0;
    }

    private static double round3(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }

    private static Double round3Nullable(Double val) {
        return val != null ? round3(val) : null;
    }
}
