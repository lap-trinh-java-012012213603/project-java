package com.analoghub.platform.search.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record LabSearchHitDto(
        String labId,
        String slug,
        String name,
        String bio,
        String fullAddress,
        String districtCode,
        String provinceCode,
        GeoCoordinateDto location,
        Double distanceKm,
        String coverImageUrl,
        List<String> supportedFormats,
        List<String> supportedProcesses,
        List<String> scannerModels,
        BigDecimal minPriceVnd,
        BigDecimal maxPriceVnd,
        Integer estimatedTurnaroundHours,
        Boolean isAcceptingOrders,
        Float avgRating,
        Integer reviewCount
) {
}

