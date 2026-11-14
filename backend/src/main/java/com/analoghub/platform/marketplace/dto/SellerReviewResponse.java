package com.analoghub.platform.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record SellerReviewResponse(
        UUID id,
        UUID marketOrderId,
        UUID reviewerUserId,
        UUID sellerUserId,
        Short rating,
        boolean itemAsDescribed,
        String comment,
        Instant createdAt
) {
}
