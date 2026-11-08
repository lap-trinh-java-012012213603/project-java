package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferCreatedResponse(
        UUID offerId,
        UUID listingId,
        UUID buyerUserId,
        UUID sellerUserId,
        String offerType,
        BigDecimal offeredPriceVnd,
        UUID swapOfferedListingId,
        String swapItemDescription,
        BigDecimal cashTopupVnd,
        BigDecimal counterAmountVnd,
        String status,
        UUID conversationId,
        UUID offerCardMessageId,
        Instant expiresAt,
        Instant createdAt
) {
}
