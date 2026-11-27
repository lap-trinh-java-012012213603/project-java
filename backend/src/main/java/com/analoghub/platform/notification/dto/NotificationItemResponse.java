package com.analoghub.platform.notification.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationItemResponse(
        UUID id,
        String notificationType,
        String title,
        String body,
        Map<String, Object> actionPayload,
        boolean isRead,
        Instant createdAt
) {
}
