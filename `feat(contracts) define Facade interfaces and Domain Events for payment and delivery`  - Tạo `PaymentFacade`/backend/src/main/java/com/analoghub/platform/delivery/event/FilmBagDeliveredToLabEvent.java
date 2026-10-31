package com.analoghub.platform.delivery.event;

import java.time.Instant;
import java.util.UUID;

public record FilmBagDeliveredToLabEvent(
        UUID eventId,
        UUID taskId,
        UUID orderId,
        UUID labId,
        String proofImageKey,
        Instant deliveredAt
) {
    public FilmBagDeliveredToLabEvent(UUID taskId, UUID orderId, UUID labId, String proofImageKey, Instant deliveredAt) {
        this(UUID.randomUUID(), taskId, orderId, labId, proofImageKey, deliveredAt);
    }
}
