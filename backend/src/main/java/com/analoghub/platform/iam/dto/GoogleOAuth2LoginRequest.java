package com.analoghub.platform.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleOAuth2LoginRequest(
        @NotBlank(message = "Google ID Token không được để trống")
        @Size(max = 4096, message = "Google ID Token vượt quá độ dài cho phép")
        String idToken,

        @Size(max = 255, message = "Thông tin thiết bị không được vượt quá 255 ký tự")
        String deviceInfo
) {
}
