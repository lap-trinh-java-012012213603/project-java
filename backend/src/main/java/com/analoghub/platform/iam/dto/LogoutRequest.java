package com.analoghub.platform.iam.dto;

import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @Size(max = 256, message = "Refresh Token vượt quá độ dài cho phép")
        String refreshToken,

        Boolean logoutAllDevices
) {
}
