package com.analoghub.platform.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DisputeOpenedResponse(
        UUID disputeId,
        String disputeCode,
        DisputeType disputeType,
        UUID referenceOrderId,
        String orderCode,
        UUID complainantUserId,
        UUID respondentUserId,
        String status,
        String escrowHoldStatus,
        BigDecimal frozenEscrowAmountVnd,
        BigDecimal requestedRefundAmountVnd,
        int evidenceCount,
        Instant respondentDeadlineAt,
        Instant createdAt
) {
}
