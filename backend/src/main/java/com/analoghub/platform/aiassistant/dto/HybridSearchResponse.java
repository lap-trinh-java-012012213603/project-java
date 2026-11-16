package com.analoghub.platform.aiassistant.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record HybridSearchResponse(
        String query,
        String scope,
        int rrfK,
        String embeddingModel,
        long latencyMs,
        List<HybridLabSearchResultDto> labResults,
        List<HybridListingSearchResultDto> listingResults,
        List<HybridKnowledgeSearchResultDto> knowledgeResults
) {

    @Builder
    public record HybridLabSearchResultDto(
            int finalRank,
            UUID labId,
            String slug,
            String name,
            String fullAddress,
            List<String> supportedProcesses,
            List<String> scannerModels,
            BigDecimal minPriceVnd,
            Double avgRating,
            double rrfFusionScore,
            RankDetailsDto rankDetails
    ) {
    }

    @Builder
    public record HybridListingSearchResultDto(
            int finalRank,
            UUID listingId,
            String slug,
            String title,
            String category,
            String brand,
            String modelName,
            String conditionGrade,
            BigDecimal priceVnd,
            String provinceCode,
            double rrfFusionScore,
            RankDetailsDto rankDetails
    ) {
    }

    @Builder
    public record HybridKnowledgeSearchResultDto(
            int finalRank,
            UUID chunkId,
            String sourceType,
            String articleSlug,
            String chunkTitle,
            String snippet,
            double similarityScore,
            double rerankScore
    ) {
    }

    @Builder
    public record RankDetailsDto(
            Integer esBm25Rank,
            Double esBm25Score,
            Integer pgvectorRank,
            Double cosineSimilarity
    ) {
    }
}
