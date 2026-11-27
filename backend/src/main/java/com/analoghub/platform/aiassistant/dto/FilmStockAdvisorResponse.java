package com.analoghub.platform.aiassistant.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record FilmStockAdvisorResponse(
        String advisorSummary,
        List<RecommendedFilmStockDto> recommendedFilmStocks,
        String providerUsed,
        String modelUsed,
        long latencyMs
) {

    @Builder
    public record RecommendedFilmStockDto(
            int rank,
            UUID filmStockId,
            String code,
            String brand,
            String name,
            int iso,
            String filmFormat,
            String processType,
            String colorBalance,
            String grainLevel,
            double matchScore,
            int recommendedEiRating,
            String exposureAdvice,
            String preferredScannerPairing,
            int compatibleLabsCount,
            int availableMarketplaceListingsCount
    ) {
    }
}
