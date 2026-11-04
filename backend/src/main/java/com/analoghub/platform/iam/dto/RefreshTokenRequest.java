package com.analoghub.platform.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh Token không được để trống")
        @Size(max = 256, message = "Refresh Token vượt quá độ dài cho phép")
        String refreshToken
) {
}
