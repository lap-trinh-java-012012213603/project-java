package com.analoghub.platform.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundProcessedEvent(
        UUID eventId,
        UUID refundId,
        UUID escrowId,
        String referenceType,
        UUID referenceId,
        BigDecimal refundAmountVnd,
        Instant occurredAt
) {
    public RefundProcessedEvent(UUID refundId, UUID escrowId, String referenceType, UUID referenceId, BigDecimal refundAmountVnd) {
        this(UUID.randomUUID(), refundId, escrowId, referenceType, referenceId, refundAmountVnd, Instant.now());
    }
}
