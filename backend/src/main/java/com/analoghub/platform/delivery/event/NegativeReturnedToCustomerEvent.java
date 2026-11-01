package com.analoghub.platform.delivery.event;

import java.time.Instant;
import java.util.UUID;

public record NegativeReturnedToCustomerEvent(
        UUID eventId,
        UUID taskId,
        UUID orderId,
        UUID customerId,
        Instant deliveredAt
) {
    public NegativeReturnedToCustomerEvent(UUID taskId, UUID orderId, UUID customerId, Instant deliveredAt) {
        this(UUID.randomUUID(), taskId, orderId, customerId, deliveredAt);
    }
}
