package com.analoghub.platform.aiassistant.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record AiChatSessionResponse(
        UUID sessionId,
        UUID userId,
        String title,
        String activeModelProvider,
        String activeModelName,
        ContextFilmStockDto contextFilmStock,
        int messageCount,
        Instant createdAt,
        Instant updatedAt
) {

    @Builder
    public record ContextFilmStockDto(
            UUID filmStockId,
            String code,
            String brand,
            String name,
            int iso,
            String processType
    ) {
    }
}
