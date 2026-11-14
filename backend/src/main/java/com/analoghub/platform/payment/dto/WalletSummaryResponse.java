package com.analoghub.platform.payment.dto;

import com.analoghub.platform.payment.domain.WalletType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletSummaryResponse(
        UUID walletId,
        UUID ownerUserId,
        WalletType walletType,
        BigDecimal availableBalanceVnd,
        BigDecimal lockedBalanceVnd,
        BigDecimal totalBalanceVnd,
        BigDecimal pendingEscrowReceivableVnd,
        String currency,
        Instant updatedAt
) {
}
