package com.analoghub.platform.marketplace.dto;

import java.util.UUID;

public record ListingImageResponse(
        UUID id,
        String s3Key,
        String imageUrl,
        int sortOrder
) {
}
