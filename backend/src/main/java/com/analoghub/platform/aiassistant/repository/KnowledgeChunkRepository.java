package com.analoghub.platform.aiassistant.repository;

import com.analoghub.platform.aiassistant.domain.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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
}
