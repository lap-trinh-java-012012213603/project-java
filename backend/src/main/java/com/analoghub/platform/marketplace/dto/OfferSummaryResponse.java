package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferSummaryResponse(
        UUID offerId,
        UUID listingId,
        BuyerSummary buyer,
        String offerType,
        BigDecimal offeredPriceVnd,
        UUID swapOfferedListingId,
        String swapItemDescription,
        BigDecimal cashTopupVnd,
        BigDecimal counterAmountVnd,
        String status,
        Instant expiresAt,
        Instant createdAt
) {
    public record BuyerSummary(
            UUID userId,
            String fullName,
            String avatarUrl
    ) {
    }
}
