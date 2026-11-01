package com.analoghub.platform.marketplace.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ListingPublishedEvent(
        UUID eventId,
        UUID listingId,
        UUID sellerId,
        String title,
        String category,
        BigDecimal priceVnd,
        String status,
        Instant occurredAt
) {
    public ListingPublishedEvent(UUID listingId, UUID sellerId, String title, String category, BigDecimal priceVnd, String status) {
        this(UUID.randomUUID(), listingId, sellerId, title, category, priceVnd, status, Instant.now());
    }
}
