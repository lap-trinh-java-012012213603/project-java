package com.analoghub.platform.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutRequestAdminDto(
        UUID payoutRequestId,
        UUID walletId,
        String walletType,
        RequesterSummary requester,
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
    public record RequesterSummary(
            UUID userId,
            String fullName,
            String email
    ) {
    }
}
