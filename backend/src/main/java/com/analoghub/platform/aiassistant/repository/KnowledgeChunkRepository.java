package com.analoghub.platform.aiassistant.repository;

import com.analoghub.platform.aiassistant.domain.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {

    @Query(value = """
            SELECT * FROM ai_knowledge_chunks
            WHERE metadata->>'sourceType' = :sourceType
              AND metadata->>'sourceRefId' = CAST(:sourceRefId AS TEXT)
            ORDER BY CAST(COALESCE(metadata->>'chunkIndex', '0') AS INTEGER) ASC
            """, nativeQuery = true)
    List<KnowledgeChunk> findBySourceTypeAndSourceRefId(
            @Param("sourceType") String sourceType,
            @Param("sourceRefId") UUID sourceRefId
    );

    @Modifying
    @Query(value = """
            DELETE FROM ai_knowledge_chunks
            WHERE metadata->>'sourceType' = :sourceType
              AND metadata->>'sourceRefId' = CAST(:sourceRefId AS TEXT)
            """, nativeQuery = true)
    int deleteBySourceTypeAndSourceRefId(
            @Param("sourceType") String sourceType,
            @Param("sourceRefId") UUID sourceRefId
    );

    @Modifying
    @Query(value = """
            DELETE FROM ai_knowledge_chunks
            WHERE metadata->>'sourceType' = :sourceType
              AND metadata->>'sourceRefId' = CAST(:sourceRefId AS TEXT)
              AND CAST(COALESCE(metadata->>'chunkIndex', '0') AS INTEGER) >= :minChunkIndex
            """, nativeQuery = true)
    int deleteBySourceTypeAndSourceRefIdAndChunkIndexGreaterThanEqual(
            @Param("sourceType") String sourceType,
            @Param("sourceRefId") UUID sourceRefId,
            @Param("minChunkIndex") int minChunkIndex
    );

    /**
     * Increases HNSW graph search depth for the current transaction (Section 5.1).
     */
    @Modifying
    @Query(value = "SET LOCAL hnsw.ef_search = 100", nativeQuery = true)
    void setLocalHnswEfSearch();

    /**
     * Native pgvector HNSW ANN Cosine Distance (<=>) Top-K query (Section 5.1).
     */
    @Query(value = """
            SELECT
                id,
                metadata->>'sourceType' AS source_type,
                metadata->>'sourceRefId' AS source_ref_id,
                CAST(COALESCE(metadata->>'chunkIndex', '0') AS INTEGER) AS chunk_index,
                metadata->>'chunkTitle' AS chunk_title,
                chunk_text AS chunk_content,
                CAST(metadata AS TEXT) AS metadata_json,
                1 - (embedding <=> CAST(:queryVector AS vector)) AS similarity_score
            FROM ai_knowledge_chunks
            WHERE embedding IS NOT NULL
              AND (:filterProcessType IS NULL OR metadata->>'processType' = :filterProcessType)
              AND (:filterFilmStock IS NULL OR metadata->>'filmStockCode' = :filterFilmStock)
              AND (:filterCategory IS NULL OR metadata->>'category' = :filterCategory)
            ORDER BY embedding <=> CAST(:queryVector AS vector) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Map<String, Object>> findTopNearestByCosineDistance(
            @Param("queryVector") String queryVectorLiteral,
            @Param("filterProcessType") String filterProcessType,
            @Param("filterFilmStock") String filterFilmStock,
            @Param("filterCategory") String filterCategory,
            @Param("limit") int limit
    );
}
