package com.analoghub.platform.aiassistant.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record AiChatSessionListResponse(
        List<AiChatSessionSummaryDto> items,
        PaginationMeta pagination
) {

    @Builder
    public record AiChatSessionSummaryDto(
            UUID sessionId,
            String title,
            String activeModelProvider,
            String activeModelName,
            String lastMessagePreview,
            int messageCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    @Builder
    public record PaginationMeta(
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
