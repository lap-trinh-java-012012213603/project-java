package com.analoghub.platform.messaging.dto;

import java.time.Instant;
import java.util.UUID;

public record MarkConversationReadResponse(
        UUID conversationId,
        UUID userId,
        int unreadCount,
        Instant lastReadAt
) {
}
