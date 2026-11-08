package com.analoghub.platform.aiassistant.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record AiChatHistoryResponse(
        UUID sessionId,
        String title,
        String activeModelProvider,
        List<AiChatMessageDto> messages,
        UUID nextCursor,
        boolean hasMore
) {

    @Builder
    public record AiChatMessageDto(
            UUID messageId,
            String senderRole,
            String messageText,
            List<RagCitationDto> citations,
            boolean safetyWarningAttached,
            boolean abstained,
            Integer promptTokens,
            Integer completionTokens,
            Long latencyMs,
            Instant createdAt
    ) {
    }
}
