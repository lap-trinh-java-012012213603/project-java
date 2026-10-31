package com.analoghub.platform.delivery.event;

import java.time.Instant;
import java.util.UUID;

public record DeliveryFailedEvent(
        UUID eventId,
        UUID taskId,
        String referenceType,
        UUID referenceId,
        String failureReason,
        Instant occurredAt
) {
    public DeliveryFailedEvent(UUID taskId, String referenceType, UUID referenceId, String failureReason) {
        this(UUID.randomUUID(), taskId, referenceType, referenceId, failureReason, Instant.now());
    }
}
