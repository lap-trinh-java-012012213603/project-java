package com.analoghub.platform.search.dto;

import lombok.Builder;

import java.util.Map;

@Builder
public record LabSearchFacetsDto(
        Map<String, Long> processCounts,
        Map<String, Long> formatCounts,
        Map<String, Long> scannerModelCounts
) {
}

