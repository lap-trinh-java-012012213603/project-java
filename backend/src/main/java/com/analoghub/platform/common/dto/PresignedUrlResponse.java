package com.analoghub.platform.common.dto;

import java.time.Instant;

public record PresignedUrlResponse(
        String url,
        String objectKey,
        String method,
        Instant expiresAt
) {
}
