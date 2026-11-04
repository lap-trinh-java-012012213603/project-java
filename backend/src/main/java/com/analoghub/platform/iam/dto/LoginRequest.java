package com.analoghub.platform.iam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 1, max = 72, message = "Mật khẩu không hợp lệ")
        String password,

        @Size(max = 255, message = "Thông tin thiết bị không được vượt quá 255 ký tự")
        String deviceInfo
) {
    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
        if (deviceInfo != null) {
            deviceInfo = deviceInfo.trim();
        }
    }
}
