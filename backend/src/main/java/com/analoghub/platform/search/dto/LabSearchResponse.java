package com.analoghub.platform.search.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record LabSearchResponse(
        List<LabSearchHitDto> items,
        LabSearchFacetsDto facets,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long tookMs
) {
}

