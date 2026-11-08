package com.analoghub.platform.aiassistant.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record RagCitationDto(
        UUID chunkId,
        String sourceType,
        UUID sourceRefId,
        String articleSlug,
        String chunkTitle,
        String quote,
        Double similarityScore,
        Double rerankScore
) {
}
