package com.analoghub.platform.messaging.dto;

import java.util.List;
import java.util.UUID;

public record CursorMessagePageResponse(
        UUID conversationId,
        String contextType,
        UUID contextReferenceId,
        List<ChatMessageResponse> items,
        String nextBeforeCursor,
        boolean hasMore
) {
}
