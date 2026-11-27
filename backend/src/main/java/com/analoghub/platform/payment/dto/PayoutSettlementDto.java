package com.analoghub.platform.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutSettlementDto(
        UUID payoutRequestId,
        UUID walletId,
        UUID requesterUserId,
        BigDecimal amountVnd,
        String previousStatus,
        String newStatus,
        String bankTransferRef,
        UUID journalGroupId,
        String entryType,
        BigDecimal availableBalanceAfterVnd,
        BigDecimal lockedBalanceAfterVnd,
        UUID reviewedByAdminId,
        Instant completedAt
) {
}
