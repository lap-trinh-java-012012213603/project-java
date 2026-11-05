package com.analoghub.platform.payment.dto;

import com.analoghub.platform.payment.domain.PayoutRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutResponse(
        UUID payoutId,
        UUID walletId,
        UUID requesterUserId,
        BigDecimal amountVnd,
        String bankCode,
        String bankAccountNo,
        String bankAccountName,
        PayoutRequestStatus status,
        BigDecimal availableBalanceAfterVnd,
        BigDecimal lockedBalanceAfterVnd,
        Instant createdAt
) {
}
