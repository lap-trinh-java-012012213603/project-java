package com.analoghub.platform.marketplace.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferSubmittedEvent(
        UUID eventId,
        UUID offerId,
        UUID listingId,
        UUID buyerId,
        UUID sellerId,
        String offerType,
        BigDecimal offeredPriceVnd,
        String status,
        Instant occurredAt
) {
    public OfferSubmittedEvent(UUID offerId, UUID listingId, UUID buyerId, UUID sellerId, String offerType, BigDecimal offeredPriceVnd, String status) {
        this(UUID.randomUUID(), offerId, listingId, buyerId, sellerId, offerType, offeredPriceVnd, status, Instant.now());
    }
}
