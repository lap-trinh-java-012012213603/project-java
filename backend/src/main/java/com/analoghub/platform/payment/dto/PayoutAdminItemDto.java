package com.analoghub.platform.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutAdminItemDto(
        UUID payoutRequestId,
        UUID walletId,
        String walletType,
        UUID requesterUserId,
        BigDecimal amountVnd,
        String bankCode,
        String bankAccountNo,
        String bankAccountName,
        String status,
        String bankTransferRef,
        UUID reviewedByAdminId,
        Instant createdAt,
        Instant completedAt
) {
}
