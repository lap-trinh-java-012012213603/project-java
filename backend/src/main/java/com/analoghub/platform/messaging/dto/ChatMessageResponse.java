package com.analoghub.platform.messaging.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ChatMessageResponse(
        UUID messageId,
        UUID conversationId,
        UUID senderUserId,
        String messageType,
        String content,
        Map<String, Object> metadataPayload,
        Instant createdAt
) {
}
