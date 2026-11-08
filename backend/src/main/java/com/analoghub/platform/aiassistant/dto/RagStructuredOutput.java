package com.analoghub.platform.aiassistant.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

/**
 * Structured output schema (Section 7.2 of 02-rag-pipeline.md) returned by the LLM.
 */
@Builder
public record RagStructuredOutput(
        String answer,
        List<RawCitationItem> citations,
        Double confidenceScore,
        Boolean abstained
) {

    @Builder
    public record RawCitationItem(
            UUID chunkId,
            String articleSlug,
            String quote
    ) {
    }
}
