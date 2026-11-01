package com.analoghub.platform.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageSentEvent(
        UUID eventId,
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        List<UUID> recipientIds,
        String previewText,
        Instant occurredAt
) {
    public MessageSentEvent(UUID messageId, UUID conversationId, UUID senderId, List<UUID> recipientIds, String previewText) {
        this(UUID.randomUUID(), messageId, conversationId, senderId, recipientIds, previewText, Instant.now());
    }
}
