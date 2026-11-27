package com.analoghub.platform.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeactivateDeviceTokenRequest(
        @NotBlank @Size(min = 32, max = 512) String fcmToken
) {
}
