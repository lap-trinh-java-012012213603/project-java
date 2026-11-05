package com.analoghub.platform.payment.dto;

import com.analoghub.platform.payment.domain.PayoutRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutSummaryDto(
        UUID payoutId,
        BigDecimal amountVnd,
        String bankCode,
        String bankAccountNo,
        String bankAccountName,
        PayoutRequestStatus status,
        String bankTransferRef,
        Instant createdAt,
        Instant completedAt
) {
}
