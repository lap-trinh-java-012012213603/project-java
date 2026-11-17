package com.analoghub.platform.notification.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateNotificationPreferencesRequest(
        @NotNull Boolean enableInApp,
        @NotNull Boolean enableEmail,
        @NotNull Boolean enablePush,
        @NotNull List<String> mutedCategories
) {
}
