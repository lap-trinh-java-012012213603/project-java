package com.analoghub.platform.delivery.event;

import java.time.Instant;
import java.util.UUID;

public record MarketParcelDeliveredEvent(
        UUID eventId,
        UUID taskId,
        UUID marketOrderId,
        UUID buyerId,
        UUID sellerId,
        Instant deliveredAt
) {
    public MarketParcelDeliveredEvent(UUID taskId, UUID marketOrderId, UUID buyerId, UUID sellerId, Instant deliveredAt) {
        this(UUID.randomUUID(), taskId, marketOrderId, buyerId, sellerId, deliveredAt);
    }
}
