package com.analoghub.platform.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EscrowReleasedEvent(
        UUID eventId,
        UUID escrowId,
        String referenceType,
        UUID referenceId,
        UUID payeeUserId,
        BigDecimal netVnd,
        Instant occurredAt
) {
    public EscrowReleasedEvent(UUID escrowId, String referenceType, UUID referenceId, UUID payeeUserId, BigDecimal netVnd) {
        this(UUID.randomUUID(), escrowId, referenceType, referenceId, payeeUserId, netVnd, Instant.now());
    }
}
