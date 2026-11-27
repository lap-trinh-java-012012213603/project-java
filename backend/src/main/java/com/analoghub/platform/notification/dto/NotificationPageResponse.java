package com.analoghub.platform.notification.dto;

import java.util.List;

public record NotificationPageResponse(
        long unreadCount,
        List<NotificationItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
