package com.analoghub.platform.search.dto;

import lombok.Builder;

import java.util.Map;

@Builder
public record ListingSearchFacetsDto(
        Map<String, Long> categoryCounts,
        Map<String, Long> conditionGradeCounts
) {
}

