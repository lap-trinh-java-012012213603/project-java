package com.analoghub.platform.notification.dto;

import java.util.UUID;

public record MarkNotificationReadResponse(
        UUID id,
        boolean isRead,
        long remainingUnreadCount
) {
}
