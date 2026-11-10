package com.analoghub.platform.iam.dto;

public record PresignedAvatarUploadResponse(
        String objectKey,
        String uploadPutUrl,
        String publicPreviewUrl,
        long expiresInSeconds
) {
}
