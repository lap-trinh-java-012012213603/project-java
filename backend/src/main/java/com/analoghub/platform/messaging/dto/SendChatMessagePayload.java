package com.analoghub.platform.messaging.dto;

import java.util.List;
import java.util.UUID;

public record SendChatMessagePayload(
        UUID clientMessageId,
        UUID conversationId,
        String messageType,
        String content,
        List<String> attachmentS3Keys
) {
}
