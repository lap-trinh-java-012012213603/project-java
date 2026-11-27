package com.analoghub.platform.aiassistant.repository;

import com.analoghub.platform.aiassistant.domain.ListingEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListingEmbeddingRepository extends JpaRepository<ListingEmbedding, UUID> {

    Optional<ListingEmbedding> findByListingId(UUID listingId);

    void deleteByListingId(UUID listingId);

    @Modifying
    @Query(value = "SET LOCAL hnsw.ef_search = 100", nativeQuery = true)
    void setLocalHnswEfSearch();

    @Query(value = """
            SELECT
                id,
                listing_id,
                features,
                1 - (embedding <=> CAST(:queryVector AS vector)) AS cosine_similarity
            FROM ai_listing_embeddings
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:queryVector AS vector) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Map<String, Object>> findTopNearestListingsByCosineDistance(
            @Param("queryVector") String queryVectorLiteral,
            @Param("limit") int limit
    );
}
