package com.analoghub.platform.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutCompletedEvent(
        UUID eventId,
        UUID payoutId,
        UUID requesterUserId,
        BigDecimal amountVnd,
        String bankRef,
        Instant occurredAt
) {
    public PayoutCompletedEvent(UUID payoutId, UUID requesterUserId, BigDecimal amountVnd, String bankRef) {
        this(UUID.randomUUID(), payoutId, requesterUserId, amountVnd, bankRef, Instant.now());
    }
}
