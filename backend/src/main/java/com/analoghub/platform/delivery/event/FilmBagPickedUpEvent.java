package com.analoghub.platform.delivery.event;

import java.time.Instant;
import java.util.UUID;

public record FilmBagPickedUpEvent(
        UUID eventId,
        UUID taskId,
        UUID orderId,
        UUID customerId,
        UUID labId,
        Instant pickedUpAt
) {
    public FilmBagPickedUpEvent(UUID taskId, UUID orderId, UUID customerId, UUID labId, Instant pickedUpAt) {
        this(UUID.randomUUID(), taskId, orderId, customerId, labId, pickedUpAt);
    }
}
