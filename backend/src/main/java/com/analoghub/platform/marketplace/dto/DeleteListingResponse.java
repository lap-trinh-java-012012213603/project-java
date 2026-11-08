package com.analoghub.platform.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record DeleteListingResponse(
        UUID id,
        Instant deletedAt,
        int cancelledPendingOffersCount
) {
}
