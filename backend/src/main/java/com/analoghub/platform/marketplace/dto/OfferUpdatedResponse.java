package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferUpdatedResponse(
        UUID offerId,
        UUID listingId,
        String offerType,
        BigDecimal offeredPriceVnd,
        BigDecimal cashTopupVnd,
        BigDecimal counterAmountVnd,
        BigDecimal agreedAmountVnd,
        String status,
        Instant expiresAt,
        Instant updatedAt
) {
}
