package com.analoghub.platform.notification.dto;

import java.util.List;
import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID userId,
        boolean enableInApp,
        boolean enableEmail,
        boolean enablePush,
        List<String> mutedCategories
) {
}
