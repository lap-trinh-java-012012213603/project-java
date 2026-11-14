package com.analoghub.platform.iam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(min = 2, max = 120, message = "Họ và tên phải từ 2 đến 120 ký tự")
        String fullName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(
                regexp = "^(0|\\+84)(3|5|7|8|9)[0-9]{8}$",
                message = "Số điện thoại di động Việt Nam không hợp lệ"
        )
        String phoneNumber,

        @Size(max = 255, message = "Khóa ảnh đại diện không được vượt quá 255 ký tự")
        @Pattern(
                regexp = "^public/avatars/.*\\.(jpg|jpeg|png|webp)$",
                message = "avatarKey phải bắt đầu bằng 'public/avatars/' và có định dạng jpg, jpeg, png hoặc webp"
        )
        String avatarKey,

        @Valid
        StylePreferencesDto stylePreferences
) {
}
