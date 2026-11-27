package com.analoghub.platform.notification.dto;

public record MarkAllNotificationsReadResponse(
        int updatedCount,
        long remainingUnreadCount
) {
}
