package com.analoghub.platform.search.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record LabAutocompleteSuggestionDto(
        String labId,
        String slug,
        String name,
        String highlightedName,
        String shortAddress,
        Float avgRating,
        BigDecimal minPriceVnd,
        Boolean isAcceptingOrders
) {
}

