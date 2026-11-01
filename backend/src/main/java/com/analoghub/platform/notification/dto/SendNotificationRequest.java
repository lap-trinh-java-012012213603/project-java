package com.analoghub.platform.notification.dto;

import java.util.Map;
import java.util.UUID;

public record SendNotificationRequest(
        UUID recipientUserId,
        String notificationType,
        String title,
        String body,
        Map<String, Object> actionPayload,
        UUID sourceEventId
) {
}
