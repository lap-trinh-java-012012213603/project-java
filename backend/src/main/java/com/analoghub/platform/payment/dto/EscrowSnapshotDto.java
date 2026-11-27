package com.analoghub.platform.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EscrowSnapshotDto(
        UUID escrowId,
        String referenceType,
        UUID referenceId,
        UUID payerUserId,
        UUID payeeUserId,
        String status,
        BigDecimal grossAmountVnd,
        BigDecimal refundedAmountVnd,
        BigDecimal remainingAmountVnd,
        BigDecimal commissionFeeVnd,
        BigDecimal netPayoutAmountVnd,
        boolean terminal,
        Instant settledAt
) {
}
