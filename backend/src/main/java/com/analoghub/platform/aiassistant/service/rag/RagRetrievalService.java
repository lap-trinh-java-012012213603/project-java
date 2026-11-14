package com.analoghub.platform.aiassistant.service.rag;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.EmbeddingClient;
import com.analoghub.platform.aiassistant.client.EmbeddingTaskType;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.KnowledgeChunk;
import com.analoghub.platform.aiassistant.repository.KnowledgeChunkRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagRetrievalService {

    public static final int INITIAL_CANDIDATES_TOP_K = 20;
    public static final int DEFAULT_RERANK_TOP_K = 5;
    public static final double DEFAULT_MMR_LAMBDA = 0.7;
    public static final double DEFAULT_ABSTENTION_THRESHOLD = 0.65;

    public static final String ABSTENTION_REASON_LOW_SIMILARITY = "TOP_SIMILARITY_BELOW_THRESHOLD";
    public static final String ABSTENTION_REASON_NO_KNOWLEDGE = "NO_KNOWLEDGE_CHUNKS_AVAILABLE";

    public static final String DEFAULT_ABSTENTION_MESSAGE_VI =
            "Xin lỗi bạn, kho tri thức nhiếp ảnh hiện tại của AnalogHub chưa có tài liệu xác thực về câu hỏi này. "
            + "Để đảm bảo an toàn cho cuộn film của bạn, hệ thống từ chối đưa ra suy đoán không có cơ sở kỹ thuật.";

    private static final Set<String> VIETNAMESE_STOPWORDS = Set.of(
            "la", "va", "cua", "co", "nhung", "cac", "mot", "nhu", "the", "nao", "bao", "nhieu",
            "cho", "voi", "khi", "trong", "tren", "duoi", "tai", "sao", "gi", "de", "duoc", "khong",
            "minh", "ban", "hoi", "ve", "cach", "lam", "ra", "vao", "tu", "den", "hay", "hoac", "neu",
            "thi", "ma", "do", "nay", "kia", "ay", "o", "dau", "ket", "qua", "toi", "hom", "so", "ty",
            "anh", "em", "ngay", "nam", "thoi", "gian", "muc", "cao", "thap", "tot", "it", "phan", "buoc"
    );

    private static final Set<String> DOMAIN_ANCHOR_TERMS = Set.of(
            "c-41", "c41", "ecn-2", "ecn2", "e-6", "e6", "bw", "film", "phim", "trang", "scan",
            "developer", "bleach", "fixer", "blix", "stabilizer", "remjet", "borax", "pb-2", "pb2",
            "sd-49", "sd49", "d-76", "d76", "rodinal", "xtol", "x-tol", "hc-110", "hc110", "id-11", "id11",
            "iso", "ei", "push", "pull", "stops", "stop", "noritsu", "frontier", "hs-1800", "sp-3000",
            "ice", "print", "archive", "archival", "slide", "buong", "nhu", "tuong", "bac", "halide",
            "nitrile", "hydroquinone", "metol", "vinegar", "kodak", "fujifilm", "fuji", "ilford",
            "cinestill", "portra", "tri-x", "trix", "hp5", "vision3", "velvia", "provia", "ektachrome",
            "nhiet", "37", "38", "20", "41", "hoa", "chat", "may", "ong", "kinh", "lo", "sang",
            "chay", "thieu", "bui", "xuoc", "contact", "sheet", "lab", "canister", "135", "120", "tiff",
            "jpeg", "pvc", "polyethylene", "polypropylene", "silica", "formalin", "formaldehyde",
            "thiosulfate", "edta", "cd-3", "cd-4", "halation", "grain", "contrast"
    );

    @Builder
    public record RetrievalQuery(
            UUID userId,
            String question,
            String filterProcessType,
            String filterFilmStock,
            String filterCategory,
            Integer topK,
            Double similarityThreshold,
            Double mmrLambda
    ) {}

    @Builder
    public record ScoredChunk(
            UUID chunkId,
            String sourceType,
            UUID sourceRefId,
            String articleSlug,
            Integer chunkIndex,
            String chunkTitle,
            String chunkContent,
            Map<String, Object> metadata,
            float[] embedding,
            double similarityScore,
            double mmrScore,
            double rerankScore
    ) {}

    @Builder
    public record RetrievalResult(
            List<ScoredChunk> selectedChunks,
            int initialCandidatesCount,
            int rerankedPassedCount,
            double topSimilarityScore,
            double topRerankScore,
            double thresholdApplied,
            boolean abstained,
            String abstentionReason,
            String abstentionMessageVi,
            String embeddingModel,
            int embeddingDimensions
    ) {}

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final AiModelRouter aiModelRouter;
    private final AiProperties aiProperties;
    private final KnowledgeSeedLoader knowledgeSeedLoader;
    private final ObjectMapper objectMapper;

    @Autowired
    public RagRetrievalService(
            KnowledgeChunkRepository knowledgeChunkRepository,
            AiModelRouter aiModelRouter,
            AiProperties aiProperties,
            KnowledgeSeedLoader knowledgeSeedLoader,
            ObjectMapper objectMapper
    ) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.aiModelRouter = aiModelRouter;
        this.aiProperties = aiProperties;
        this.knowledgeSeedLoader = knowledgeSeedLoader;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public RagRetrievalService(
            KnowledgeChunkRepository knowledgeChunkRepository,
            AiModelRouter aiModelRouter,
            AiProperties aiProperties,
            KnowledgeSeedLoader knowledgeSeedLoader
    ) {
        this(knowledgeChunkRepository, aiModelRouter, aiProperties, knowledgeSeedLoader, new ObjectMapper());
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieve(UUID userId, String question) {
        return retrieve(RetrievalQuery.builder()
                .userId(userId)
                .question(question)
                .topK(DEFAULT_RERANK_TOP_K)
                .similarityThreshold(DEFAULT_ABSTENTION_THRESHOLD)
                .mmrLambda(DEFAULT_MMR_LAMBDA)
                .build());
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieve(
            UUID userId,
            String question,
            String filterProcessType,
            String filterFilmStock,
            int topK
    ) {
        return retrieve(RetrievalQuery.builder()
                .userId(userId)
                .question(question)
                .filterProcessType(filterProcessType)
                .filterFilmStock(filterFilmStock)
                .topK(topK)
                .similarityThreshold(DEFAULT_ABSTENTION_THRESHOLD)
                .mmrLambda(DEFAULT_MMR_LAMBDA)
                .build());
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieve(RetrievalQuery request) {
        String question = request != null && request.question() != null ? request.question().trim() : "";
        int targetTopK = request != null && request.topK() != null && request.topK() > 0
                ? request.topK()
                : DEFAULT_RERANK_TOP_K;
        double threshold = request != null && request.similarityThreshold() != null
                ? request.similarityThreshold()
                : DEFAULT_ABSTENTION_THRESHOLD;
        double lambda = request != null && request.mmrLambda() != null
                ? request.mmrLambda()
                : DEFAULT_MMR_LAMBDA;

        String embeddingModel = resolveEmbeddingModelName();
        int dimensions = EmbeddingClient.REQUIRED_DIMENSIONS;

        if (question.isBlank()) {
            return buildAbstainedResult(0, 0.0, 0.0, threshold, ABSTENTION_REASON_LOW_SIMILARITY, embeddingModel, dimensions);
        }

        float[] queryVector = aiModelRouter.embed(
                request != null ? request.userId() : null,
                question,
                EmbeddingTaskType.RETRIEVAL_QUERY
        );

        List<ScoredChunk> top20Candidates = fetchTopCandidates(
                question,
                queryVector,
                request != null ? request.filterProcessType() : null,
                request != null ? request.filterFilmStock() : null,
                request != null ? request.filterCategory() : null,
                INITIAL_CANDIDATES_TOP_K
        );

        if (top20Candidates.isEmpty()) {
            return buildAbstainedResult(0, 0.0, 0.0, threshold, ABSTENTION_REASON_NO_KNOWLEDGE, embeddingModel, dimensions);
        }

        List<ScoredChunk> mmrReranked = rerankWithMmr(top20Candidates, targetTopK, lambda);

        double maxSimilarityScore = mmrReranked.stream()
                .mapToDouble(ScoredChunk::similarityScore)
                .max()
                .orElse(0.0);
        double maxRerankScore = mmrReranked.stream()
                .mapToDouble(ScoredChunk::rerankScore)
                .max()
                .orElse(0.0);

        // Section 6.2: Abstention check — if max(S_query) < 0.65, abstain immediately
        if (maxSimilarityScore < threshold) {
            log.info("RAG Abstention triggered for query='{}': topSimilarityScore={} < threshold={}",
                    question, String.format(Locale.ROOT, "%.3f", maxSimilarityScore), threshold);
            return buildAbstainedResult(
                    top20Candidates.size(),
                    maxSimilarityScore,
                    maxRerankScore,
                    threshold,
                    ABSTENTION_REASON_LOW_SIMILARITY,
                    embeddingModel,
                    dimensions
            );
        }

        List<ScoredChunk> passedChunks = mmrReranked.stream()
                .filter(c -> c.similarityScore() >= threshold * 0.85)
                .toList();
        if (passedChunks.isEmpty()) {
            passedChunks = List.of(mmrReranked.getFirst());
        }

        return RetrievalResult.builder()
                .selectedChunks(passedChunks)
                .initialCandidatesCount(top20Candidates.size())
                .rerankedPassedCount(passedChunks.size())
                .topSimilarityScore(round3(maxSimilarityScore))
                .topRerankScore(round3(maxRerankScore))
                .thresholdApplied(threshold)
                .abstained(false)
                .abstentionReason(null)
                .abstentionMessageVi(null)
                .embeddingModel(embeddingModel)
                .embeddingDimensions(dimensions)
                .build();
    }

    /**
     * Maximal Marginal Relevance (MMR) reranking (Section 6.1):
     * MMR_Score(c_i) = lambda * S_query(c_i) - (1 - lambda) * max_{c_j in Selected} S_chunk(c_i, c_j)
     */
    public List<ScoredChunk> rerankWithMmr(List<ScoredChunk> candidates, int topK, double lambda) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }

        List<ScoredChunk> remaining = new ArrayList<>(candidates);
        remaining.sort(Comparator.comparingDouble(ScoredChunk::similarityScore).reversed());

        List<ScoredChunk> selected = new ArrayList<>(Math.min(topK, remaining.size()));

        while (!remaining.isEmpty() && selected.size() < topK) {
            ScoredChunk bestCandidate = null;
            double bestMmrScore = Double.NEGATIVE_INFINITY;
            int bestIndex = -1;

            for (int i = 0; i < remaining.size(); i++) {
                ScoredChunk candidate = remaining.get(i);
                double sQuery = candidate.similarityScore();

                double maxSimToSelected = 0.0;
                for (ScoredChunk alreadyChosen : selected) {
                    double interChunkSim = computeInterChunkSimilarity(candidate, alreadyChosen);
                    if (interChunkSim > maxSimToSelected) {
                        maxSimToSelected = interChunkSim;
                    }
                }

                double mmrScore = selected.isEmpty()
                        ? sQuery
                        : (lambda * sQuery) - ((1.0 - lambda) * maxSimToSelected);

                if (mmrScore > bestMmrScore) {
                    bestMmrScore = mmrScore;
                    bestCandidate = candidate;
                    bestIndex = i;
                }
            }

            if (bestCandidate == null) {
                break;
            }

            remaining.remove(bestIndex);
            double rerankScore = selected.isEmpty()
                    ? bestCandidate.similarityScore()
                    : Math.max(0.0, Math.min(1.0, 0.5 * bestCandidate.similarityScore() + 0.5 * bestMmrScore));

            selected.add(ScoredChunk.builder()
                    .chunkId(bestCandidate.chunkId())
                    .sourceType(bestCandidate.sourceType())
                    .sourceRefId(bestCandidate.sourceRefId())
                    .articleSlug(bestCandidate.articleSlug())
                    .chunkIndex(bestCandidate.chunkIndex())
                    .chunkTitle(bestCandidate.chunkTitle())
                    .chunkContent(bestCandidate.chunkContent())
                    .metadata(bestCandidate.metadata())
                    .embedding(bestCandidate.embedding())
                    .similarityScore(round3(bestCandidate.similarityScore()))
                    .mmrScore(round3(bestMmrScore))
                    .rerankScore(round3(rerankScore))
                    .build());
        }

        return Collections.unmodifiableList(selected);
    }

    private List<ScoredChunk> fetchTopCandidates(
            String question,
            float[] queryVector,
            String filterProcessType,
            String filterFilmStock,
            String filterCategory,
            int limit
    ) {
        try {
            knowledgeChunkRepository.setLocalHnswEfSearch();
        } catch (Exception ex) {
            log.debug("Skipping SET LOCAL hnsw.ef_search = 100 (non-Postgres or read-only context): {}", ex.getMessage());
        }

        try {
            String vectorLiteral = toPgVectorLiteral(queryVector);
            List<Map<String, Object>> nativeRows = knowledgeChunkRepository.findTopNearestByCosineDistance(
                    vectorLiteral,
                    normalizeFilter(filterProcessType),
                    normalizeFilter(filterFilmStock),
                    normalizeFilter(filterCategory),
                    limit
            );
            if (nativeRows != null && !nativeRows.isEmpty()) {
                List<ScoredChunk> mapped = mapNativeRows(nativeRows, question);
                if (!mapped.isEmpty()) {
                    return mapped;
                }
            }
        } catch (Exception ex) {
            log.debug("Native pgvector HNSW query unavailable, falling back to repository/seed evaluation: {}",
                    ex.getMessage());
        }

        List<KnowledgeChunk> allChunks = new ArrayList<>();
        try {
            List<KnowledgeChunk> dbChunks = knowledgeChunkRepository.findAll();
            if (dbChunks != null && !dbChunks.isEmpty()) {
                allChunks.addAll(dbChunks);
            }
        } catch (Exception ex) {
            log.debug("Repository findAll fallback skipped: {}", ex.getMessage());
        }

        if (allChunks.isEmpty() && knowledgeSeedLoader != null) {
            allChunks.addAll(buildSeedChunksFromLoader());
        }

        return scoreAndFilterChunksInMemory(
                allChunks,
                question,
                queryVector,
                normalizeFilter(filterProcessType),
                normalizeFilter(filterFilmStock),
                normalizeFilter(filterCategory),
                limit
        );
    }

    private List<ScoredChunk> scoreAndFilterChunksInMemory(
            List<KnowledgeChunk> chunks,
            String question,
            float[] queryVector,
            String filterProcessType,
            String filterFilmStock,
            String filterCategory,
            int limit
    ) {
        List<ScoredChunk> scored = new ArrayList<>();

        for (KnowledgeChunk chunk : chunks) {
            chunk.syncMetadataToFields();
            Map<String, Object> meta = chunk.getMetadata() != null ? chunk.getMetadata() : Map.of();

            if (filterProcessType != null && !filterProcessType.equalsIgnoreCase(asString(meta.get("processType")))) {
                continue;
            }
            if (filterFilmStock != null && !filterFilmStock.equalsIgnoreCase(asString(meta.get("filmStockCode")))) {
                continue;
            }
            if (filterCategory != null && !filterCategory.equalsIgnoreCase(asString(meta.get("category")))) {
                continue;
            }

            double vectorCosine = (chunk.getEmbedding() != null && queryVector != null)
                    ? cosineSimilarity(queryVector, chunk.getEmbedding())
                    : 0.0;
            double lexicalSim = computeVietnameseLexicalRelevance(question, chunk.getChunkTitle(), chunk.getChunkContent());
            double combinedSimilarity = Math.max(vectorCosine, lexicalSim);

            UUID id = chunk.getId() != null
                    ? chunk.getId()
                    : UUID.nameUUIDFromBytes((chunk.getChunkContent() != null ? chunk.getChunkContent() : "").getBytes());

            scored.add(ScoredChunk.builder()
                    .chunkId(id)
                    .sourceType(chunk.getSourceType() != null ? chunk.getSourceType() : asString(meta.get("sourceType")))
                    .sourceRefId(chunk.getSourceRefId())
                    .articleSlug(asString(meta.get("articleSlug")))
                    .chunkIndex(chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0)
                    .chunkTitle(chunk.getChunkTitle())
                    .chunkContent(chunk.getChunkContent())
                    .metadata(meta)
                    .embedding(chunk.getEmbedding())
                    .similarityScore(round3(combinedSimilarity))
                    .mmrScore(round3(combinedSimilarity))
                    .rerankScore(round3(combinedSimilarity))
                    .build());
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::similarityScore).reversed());
        return scored.size() <= limit ? scored : scored.subList(0, limit);
    }

    private List<ScoredChunk> mapNativeRows(List<Map<String, Object>> rows, String question) {
        List<ScoredChunk> results = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            UUID id = toUuid(row.get("id"));
            String sourceType = asString(row.get("source_type"));
            UUID sourceRefId = toUuid(row.get("source_ref_id"));
            Integer chunkIndex = row.get("chunk_index") instanceof Number n ? n.intValue() : 0;
            String chunkTitle = asString(row.get("chunk_title"));
            String chunkContent = asString(row.get("chunk_content"));
            Map<String, Object> metadata = parseMetadataJson(asString(row.get("metadata_json")));

            double dbSim = row.get("similarity_score") instanceof Number n ? n.doubleValue() : 0.0;
            double lexicalSim = computeVietnameseLexicalRelevance(question, chunkTitle, chunkContent);
            double similarity = Math.max(dbSim, lexicalSim);

            results.add(ScoredChunk.builder()
                    .chunkId(id)
                    .sourceType(sourceType != null ? sourceType : asString(metadata.get("sourceType")))
                    .sourceRefId(sourceRefId)
                    .articleSlug(asString(metadata.get("articleSlug")))
                    .chunkIndex(chunkIndex)
                    .chunkTitle(chunkTitle != null ? chunkTitle : asString(metadata.get("chunkTitle")))
                    .chunkContent(chunkContent)
                    .metadata(metadata)
                    .similarityScore(round3(similarity))
                    .mmrScore(round3(similarity))
                    .rerankScore(round3(similarity))
                    .build());
        }
        results.sort(Comparator.comparingDouble(ScoredChunk::similarityScore).reversed());
        return results;
    }

    private List<KnowledgeChunk> buildSeedChunksFromLoader() {
        List<KnowledgeSeedLoader.SeedDocument> seedDocs = knowledgeSeedLoader.getLoadedDocuments();
        if (seedDocs.isEmpty()) {
            seedDocs = knowledgeSeedLoader.loadSeedDocuments();
        }
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (KnowledgeSeedLoader.SeedDocument doc : seedDocs) {
            for (MarkdownHeadingChunker.ChunkRaw raw : doc.chunks()) {
                Map<String, Object> meta = new LinkedHashMap<>(doc.metadata());
                KnowledgeChunk chunk = KnowledgeChunk.builder()
                        .id(UUID.nameUUIDFromBytes((doc.fileName() + ":" + raw.chunkIndex()).getBytes()))
                        .sourceType(doc.sourceType())
                        .sourceRefId(doc.sourceRefId())
                        .chunkIndex(raw.chunkIndex())
                        .chunkTitle(raw.chunkTitle())
                        .chunkContent(raw.chunkContent())
                        .contentSha256(raw.contentSha256())
                        .metadata(meta)
                        .build();
                chunk.syncFieldsToMetadata();
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    static double computeInterChunkSimilarity(ScoredChunk a, ScoredChunk b) {
        if (a == null || b == null) {
            return 0.0;
        }
        double vecSim = 0.0;
        if (a.embedding() != null && b.embedding() != null
                && a.embedding().length == b.embedding().length
                && a.embedding().length > 0) {
            vecSim = Math.max(0.0, cosineSimilarity(a.embedding(), b.embedding()));
        }
        double jaccard = computeTokenJaccardSimilarity(a.chunkContent(), b.chunkContent());
        return Math.max(vecSim, jaccard);
    }

    static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0.0;
        }
        int len = Math.min(a.length, b.length);
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        if (denom < 1e-12) {
            return 0.0;
        }
        return dot / denom;
    }

    static double computeVietnameseLexicalRelevance(String query, String chunkTitle, String chunkContent) {
        Set<String> queryTokens = tokenizeFoldedVietnamese(query);
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> titleTokens = tokenizeFoldedVietnamese(chunkTitle);
        Set<String> contentTokens = tokenizeFoldedVietnamese(chunkContent);
        if (titleTokens.isEmpty() && contentTokens.isEmpty()) {
            return 0.0;
        }

        int matchedInContent = 0;
        int matchedInTitle = 0;
        int matchedDomainTerms = 0;
        for (String qt : queryTokens) {
            boolean inChunk = contentTokens.contains(qt) || titleTokens.contains(qt);
            if (inChunk) {
                matchedInContent++;
                if (DOMAIN_ANCHOR_TERMS.contains(qt)) {
                    matchedDomainTerms++;
                }
            }
            if (titleTokens.contains(qt)) {
                matchedInTitle++;
            }
        }

        if (matchedInContent == 0 || matchedDomainTerms == 0) {
            return Math.min(0.25, (double) matchedInContent / Math.max(4, queryTokens.size()) * 0.25);
        }

        double coverage = (double) matchedInContent / queryTokens.size();
        double domainBonus = Math.min(0.28, matchedDomainTerms * 0.09);
        double titleBoost = 0.16 * matchedInTitle / queryTokens.size();

        if (matchedInContent == 1 && queryTokens.size() > 1) {
            return Math.min(0.45, coverage * 0.55);
        }

        double rawScore = 0.44 + (0.36 * coverage) + domainBonus + titleBoost;
        return Math.min(0.96, rawScore);
    }

    static double computeTokenJaccardSimilarity(String textA, String textB) {
        Set<String> tokensA = tokenizeFoldedVietnamese(textA);
        Set<String> tokensB = tokenizeFoldedVietnamese(textB);
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String t : tokensA) {
            if (tokensB.contains(t)) {
                intersection++;
            }
        }
        int union = tokensA.size() + tokensB.size() - intersection;
        return union > 0 ? (double) intersection / union : 0.0;
    }

    static Set<String> tokenizeFoldedVietnamese(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);

        String[] parts = normalized.split("[^a-z0-9\\-]+");
        Set<String> tokens = new HashSet<>();
        for (String p : parts) {
            String clean = p.trim();
            if (clean.length() >= 2 && !VIETNAMESE_STOPWORDS.contains(clean)) {
                tokens.add(clean);
            }
        }
        return tokens;
    }

    static String toPgVectorLiteral(float[] vector) {
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

    private RetrievalResult buildAbstainedResult(
            int initialCandidatesCount,
            double topSimilarityScore,
            double topRerankScore,
            double threshold,
            String reason,
            String embeddingModel,
            int dimensions
    ) {
        return RetrievalResult.builder()
                .selectedChunks(List.of())
                .initialCandidatesCount(initialCandidatesCount)
                .rerankedPassedCount(0)
                .topSimilarityScore(round3(topSimilarityScore))
                .topRerankScore(round3(topRerankScore))
                .thresholdApplied(threshold)
                .abstained(true)
                .abstentionReason(reason)
                .abstentionMessageVi(DEFAULT_ABSTENTION_MESSAGE_VI)
                .embeddingModel(embeddingModel)
                .embeddingDimensions(dimensions)
                .build();
    }

    private Map<String, Object> parseMetadataJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String resolveEmbeddingModelName() {
        ModelProvider provider = aiProperties.resolveEmbeddingProvider();
        if (provider == ModelProvider.OPENAI) {
            return aiProperties.getOpenai().getEmbeddingModel();
        }
        return aiProperties.getGemini().getEmbeddingModel();
    }

    private static String normalizeFilter(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String asString(Object val) {
        return val != null ? String.valueOf(val) : null;
    }

    private static UUID toUuid(Object val) {
        if (val instanceof UUID u) {
            return u;
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
