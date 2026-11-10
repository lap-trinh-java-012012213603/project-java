package com.analoghub.platform.marketplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PresignListingImageRequest(
        @NotBlank(message = "Tên file ảnh không được để trống")
        String fileName,

        @NotBlank(message = "Định dạng contentType không được để trống")
        String contentType,

        @Min(value = 1, message = "Kích thước file phải lớn hơn 0")
        long fileSizeBytes
) {
}
