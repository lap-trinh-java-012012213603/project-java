package com.analoghub.platform.aiassistant.service.rag;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.EmbeddingClient;
import com.analoghub.platform.aiassistant.client.EmbeddingTaskType;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.KnowledgeChunk;
import com.analoghub.platform.aiassistant.repository.KnowledgeChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestionService implements KnowledgeSeedLoader.SeedDocumentConsumer {

    public static final String SOURCE_TYPE_EXPERT_ARTICLE = "EXPERT_ARTICLE";
    public static final String SOURCE_TYPE_SEED_DOC = KnowledgeSeedLoader.SOURCE_TYPE_SEED_DOC;
    public static final String SOURCE_TYPE_FILM_STOCK_CATALOG = "FILM_STOCK_CATALOG";

    public record IngestionResult(
            String sourceType,
            UUID sourceRefId,
            int totalChunks,
            int embeddedAndUpsertedCount,
            int skippedUnchangedCount,
            int deletedStaleCount
    ) {}

    private final MarkdownHeadingChunker chunker;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final AiModelRouter aiModelRouter;
    private final AiProperties aiProperties;

    @Override
    @Transactional
    public void accept(KnowledgeSeedLoader.SeedDocument seedDocument) {
        ingestSeedDocument(seedDocument);
    }

    @Transactional
    public IngestionResult ingestSeedDocument(KnowledgeSeedLoader.SeedDocument seedDocument) {
        if (seedDocument == null) {
            return new IngestionResult(SOURCE_TYPE_SEED_DOC, null, 0, 0, 0, 0);
        }
        List<MarkdownHeadingChunker.ChunkRaw> rawChunks = seedDocument.chunks() != null
                ? seedDocument.chunks()
                : List.of();
        return ingestChunks(
                SOURCE_TYPE_SEED_DOC,
                seedDocument.sourceRefId(),
                rawChunks,
                seedDocument.metadata()
        );
    }

    @Transactional
    public IngestionResult ingestArticleContent(
            UUID articleId,
            String slug,
            String title,
            String markdownContent
    ) {
        return ingestArticleContent(articleId, slug, title, "DARKROOM_TECHNIQUE", markdownContent);
    }

    @Transactional
    public IngestionResult ingestArticleContent(
            UUID articleId,
            String slug,
            String title,
            String category,
            String markdownContent
    ) {
        if (articleId == null || markdownContent == null || markdownContent.isBlank()) {
            return new IngestionResult(SOURCE_TYPE_EXPERT_ARTICLE, articleId, 0, 0, 0, 0);
        }

        List<MarkdownHeadingChunker.ChunkRaw> rawChunks = chunker.split(markdownContent, title);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "COMMUNITY_EXPERT");
        if (slug != null && !slug.isBlank()) {
            metadata.put("articleSlug", slug);
        }
        if (category != null && !category.isBlank()) {
            metadata.put("category", category);
        }

        return ingestChunks(SOURCE_TYPE_EXPERT_ARTICLE, articleId, rawChunks, metadata);
    }

    @Transactional
    public IngestionResult ingestFilmStockProfile(
            UUID filmStockId,
            String brand,
            String name,
            String format,
            String processType,
            int iso,
            String filmStockCode,
            String toneDescription
    ) {
        if (filmStockId == null) {
            return new IngestionResult(SOURCE_TYPE_FILM_STOCK_CATALOG, null, 0, 0, 0, 0);
        }

        String profileText = String.format(
                "[Film Stock Profile] Brand: %s | Name: %s | Format: %s | Process: %s | ISO: %d | Tone: %s",
                brand != null ? brand : "",
                name != null ? name : "",
                format != null ? format : "135 & 120",
                processType != null ? processType : "C41",
                iso,
                toneDescription != null ? toneDescription : ""
        );
        String title = "Thông số kỹ thuật " + (brand != null ? brand + " " : "") + (name != null ? name : "");
        String sha256 = chunker.calculateSha256(profileText);

        MarkdownHeadingChunker.ChunkRaw singleChunk = new MarkdownHeadingChunker.ChunkRaw(
                0,
                title,
                "[Film Stock Profile]",
                profileText,
                sha256,
                chunker.estimateTokens(profileText)
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "CATALOG");
        metadata.put("brand", brand);
        metadata.put("filmStockCode", filmStockCode);
        metadata.put("iso", iso);
        metadata.put("processType", processType);
        if (filmStockCode != null) {
            metadata.put("articleSlug", "film-stock-" + filmStockCode.toLowerCase().replace('_', '-'));
        }

        return ingestChunks(SOURCE_TYPE_FILM_STOCK_CATALOG, filmStockId, List.of(singleChunk), metadata);
    }

    @Transactional
    public int retractArticleContent(UUID articleId) {
        if (articleId == null) {
            return 0;
        }
        int deleted = knowledgeChunkRepository.deleteBySourceTypeAndSourceRefId(
                SOURCE_TYPE_EXPERT_ARTICLE,
                articleId
        );
        log.info("Retracted RAG knowledge chunks for articleId={}: deleted {} chunks.", articleId, deleted);
        return deleted;
    }

    @Transactional
    public IngestionResult ingestChunks(
            String sourceType,
            UUID sourceRefId,
            List<MarkdownHeadingChunker.ChunkRaw> rawChunks,
            Map<String, Object> baseMetadata
    ) {
        List<MarkdownHeadingChunker.ChunkRaw> safeChunks = rawChunks != null ? rawChunks : List.of();
        List<KnowledgeChunk> existingChunks = knowledgeChunkRepository.findBySourceTypeAndSourceRefId(
                sourceType,
                sourceRefId
        );

        Map<Integer, KnowledgeChunk> existingByIndex = new HashMap<>();
        if (existingChunks != null) {
            for (KnowledgeChunk existing : existingChunks) {
                existing.syncMetadataToFields();
                if (existing.getChunkIndex() != null) {
                    existingByIndex.put(existing.getChunkIndex(), existing);
                }
            }
        }

        List<MarkdownHeadingChunker.ChunkRaw> chunksToEmbed = new ArrayList<>();
        int skippedCount = 0;

        for (MarkdownHeadingChunker.ChunkRaw raw : safeChunks) {
            KnowledgeChunk existing = existingByIndex.get(raw.chunkIndex());
            if (existing != null && raw.contentSha256().equals(existing.getContentSha256())) {
                skippedCount++;
            } else {
                chunksToEmbed.add(raw);
            }
        }

        int upsertedCount = 0;
        if (!chunksToEmbed.isEmpty()) {
            List<String> textsToEmbed = chunksToEmbed.stream()
                    .map(MarkdownHeadingChunker.ChunkRaw::chunkContent)
                    .toList();

            List<float[]> vectors = aiModelRouter.embedBatch(
                    null,
                    textsToEmbed,
                    EmbeddingTaskType.RETRIEVAL_DOCUMENT
            );

            String embeddingModelName = resolveEmbeddingModelName();
            List<KnowledgeChunk> entitiesToUpsert = new ArrayList<>(chunksToEmbed.size());

            for (int i = 0; i < chunksToEmbed.size(); i++) {
                MarkdownHeadingChunker.ChunkRaw raw = chunksToEmbed.get(i);
                float[] vector = (vectors != null && i < vectors.size())
                        ? vectors.get(i)
                        : new float[EmbeddingClient.REQUIRED_DIMENSIONS];

                KnowledgeChunk target = existingByIndex.getOrDefault(raw.chunkIndex(), new KnowledgeChunk());
                target.setSourceType(sourceType);
                target.setSourceRefId(sourceRefId);
                target.setChunkIndex(raw.chunkIndex());
                target.setChunkTitle(raw.chunkTitle());
                target.setChunkContent(raw.chunkContent());
                target.setContentSha256(raw.contentSha256());
                target.setEmbeddingModel(embeddingModelName);
                target.setDimensions(EmbeddingClient.REQUIRED_DIMENSIONS);
                target.setEmbedding(vector);

                Map<String, Object> chunkMetadata = new LinkedHashMap<>();
                if (baseMetadata != null) {
                    chunkMetadata.putAll(baseMetadata);
                }
                target.setMetadata(chunkMetadata);
                target.syncFieldsToMetadata();

                entitiesToUpsert.add(target);
            }

            knowledgeChunkRepository.saveAll(entitiesToUpsert);
            upsertedCount = entitiesToUpsert.size();
        }

        int newCount = safeChunks.size();
        long staleInMemoryCount = existingByIndex.keySet().stream()
                .filter(idx -> idx >= newCount)
                .count();

        int deletedStaleCount = 0;
        if (staleInMemoryCount > 0) {
            deletedStaleCount = knowledgeChunkRepository.deleteBySourceTypeAndSourceRefIdAndChunkIndexGreaterThanEqual(
                    sourceType,
                    sourceRefId,
                    newCount
            );
            if (deletedStaleCount == 0) {
                deletedStaleCount = (int) staleInMemoryCount;
            }
        }

        log.info("RAG ingestion completed [sourceType={}, sourceRefId={}]: total={}, upserted={}, skipped={}, deletedStale={}",
                sourceType, sourceRefId, newCount, upsertedCount, skippedCount, deletedStaleCount);

        return new IngestionResult(
                sourceType,
                sourceRefId,
                newCount,
                upsertedCount,
                skippedCount,
                deletedStaleCount
        );
    }

    private String resolveEmbeddingModelName() {
        ModelProvider provider = aiProperties.resolveEmbeddingProvider();
        if (provider == ModelProvider.OPENAI) {
            return aiProperties.getOpenai().getEmbeddingModel();
        }
        return aiProperties.getGemini().getEmbeddingModel();
    }
}
