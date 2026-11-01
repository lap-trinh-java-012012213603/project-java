package com.analoghub.platform.marketplace.event;

import java.time.Instant;
import java.util.UUID;

public record ListingArchivedEvent(
        UUID eventId,
        UUID listingId,
        UUID sellerId,
        String status,
        Instant occurredAt
) {
    public ListingArchivedEvent(UUID listingId, UUID sellerId, String status) {
        this(UUID.randomUUID(), listingId, sellerId, status, Instant.now());
    }
}
