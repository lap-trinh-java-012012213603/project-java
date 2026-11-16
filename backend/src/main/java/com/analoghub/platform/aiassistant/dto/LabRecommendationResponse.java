package com.analoghub.platform.aiassistant.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record LabRecommendationResponse(
        UUID userId,
        boolean coldStartMode,
        WeightsAppliedDto weightsApplied,
        List<RecommendedLabItemDto> recommendations
) {

    @Builder
    public record WeightsAppliedDto(
            double wCf,
            double wCb,
            double wGeo,
            double wSla
    ) {
    }

    @Builder
    public record RecommendedLabItemDto(
            int rank,
            UUID labId,
            String slug,
            String name,
            String fullAddress,
            Double distanceKm,
            double avgRating,
            int reviewCount,
            BigDecimal minPriceVnd,
            int estimatedTurnaroundHours,
            List<String> supportedProcesses,
            List<String> scannerModels,
            double finalHybridScore,
            ScoreBreakdownDto scoreBreakdown,
            String explanationVi
    ) {
    }

    @Builder
    public record ScoreBreakdownDto(
            double cfScore,
            double cbCosineScore,
            double geoDecayScore,
            double slaReliabilityScore
    ) {
    }
}
