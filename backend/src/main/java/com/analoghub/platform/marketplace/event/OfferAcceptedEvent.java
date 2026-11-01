package com.analoghub.platform.marketplace.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferAcceptedEvent(
        UUID eventId,
        UUID offerId,
        UUID listingId,
        UUID buyerId,
        UUID sellerId,
        String offerType,
        BigDecimal agreedAmountVnd,
        String status,
        Instant occurredAt
) {
    public OfferAcceptedEvent(UUID offerId, UUID listingId, UUID buyerId, UUID sellerId, String offerType, BigDecimal agreedAmountVnd, String status) {
        this(UUID.randomUUID(), offerId, listingId, buyerId, sellerId, offerType, agreedAmountVnd, status, Instant.now());
    }
}
