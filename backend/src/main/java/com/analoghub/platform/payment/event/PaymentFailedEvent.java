package com.analoghub.platform.payment.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        UUID txnId,
        String paymentPurpose,
        UUID referenceId,
        String reason,
        Instant occurredAt
) {
    public PaymentFailedEvent(UUID txnId, String paymentPurpose, UUID referenceId, String reason) {
        this(UUID.randomUUID(), txnId, paymentPurpose, referenceId, reason, Instant.now());
    }
}
