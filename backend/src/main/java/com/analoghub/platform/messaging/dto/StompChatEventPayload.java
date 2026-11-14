package com.analoghub.platform.messaging.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StompChatEventPayload(
        String eventType,
        UUID clientMessageId,
        MessageDetail message
) {
    public record MessageDetail(
            UUID messageId,
            UUID conversationId,
            String contextType,
            UUID contextReferenceId,
            SenderSummary sender,
            String messageType,
            String content,
            Map<String, Object> metadataPayload,
            Instant createdAt
    ) {
    }

    public record SenderSummary(
            UUID userId,
            String fullName,
            String avatarUrl
    ) {
    }
}
