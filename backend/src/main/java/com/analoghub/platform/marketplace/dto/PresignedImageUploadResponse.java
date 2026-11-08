package com.analoghub.platform.marketplace.dto;

public record PresignedImageUploadResponse(
        String objectKey,
        String uploadPutUrl,
        String publicPreviewUrl,
        long expiresInSeconds
) {
}
