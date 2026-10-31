package com.analoghub.platform.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        UUID txnId,
        String paymentPurpose,
        UUID referenceId,
        UUID payerId,
        BigDecimal amountVnd,
        Instant occurredAt
) {
    public PaymentSucceededEvent(UUID txnId, String paymentPurpose, UUID referenceId, UUID payerId, BigDecimal amountVnd) {
        this(UUID.randomUUID(), txnId, paymentPurpose, referenceId, payerId, amountVnd, Instant.now());
    }
}
