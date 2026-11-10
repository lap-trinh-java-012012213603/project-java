package com.analoghub.platform.iam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PresignAvatarUploadRequest(
        @NotBlank(message = "Tên file ảnh đại diện không được để trống")
        String fileName,

        @NotBlank(message = "Định dạng contentType không được để trống")
        String contentType,

        @Min(value = 1, message = "Kích thước file phải lớn hơn 0")
        long fileSizeBytes
) {
}
