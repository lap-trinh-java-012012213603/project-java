package com.analoghub.platform.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceTokenResponse(
        UUID id,
        UUID userId,
        String devicePlatform,
        boolean isActive,
        Instant updatedAt
) {
}
