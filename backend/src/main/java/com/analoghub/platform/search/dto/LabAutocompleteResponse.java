package com.analoghub.platform.search.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record LabAutocompleteResponse(
        List<LabAutocompleteSuggestionDto> suggestions,
        long tookMs
) {
}

