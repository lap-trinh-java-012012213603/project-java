package com.analoghub.platform.messaging.dto;

import java.time.Instant;
import java.util.UUID;

public record StompErrorPayload(
        boolean success,
        String errorCode,
        UUID clientMessageId,
        String message,
        Instant timestamp
) {
    public static StompErrorPayload of(String errorCode, UUID clientMessageId, String message) {
        return new StompErrorPayload(false, errorCode, clientMessageId, message, Instant.now());
    }
}
