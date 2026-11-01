package com.analoghub.platform.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BlankRollRefundProcessedEvent(
        UUID eventId,
        UUID escrowId,
        UUID orderId,
        UUID rollId,
        UUID customerId,
        BigDecimal refundedScanFeeVnd,
        Instant occurredAt
) {
    public BlankRollRefundProcessedEvent(UUID escrowId, UUID orderId, UUID rollId, UUID customerId, BigDecimal refundedScanFeeVnd) {
        this(UUID.randomUUID(), escrowId, orderId, rollId, customerId, refundedScanFeeVnd, Instant.now());
    }
}
