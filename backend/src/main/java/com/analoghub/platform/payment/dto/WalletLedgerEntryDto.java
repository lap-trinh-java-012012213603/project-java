package com.analoghub.platform.payment.dto;

import com.analoghub.platform.payment.domain.LedgerEntryDirection;
import com.analoghub.platform.payment.domain.LedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletLedgerEntryDto(
        UUID ledgerEntryId,
        UUID journalGroupId,
        UUID walletId,
        UUID paymentTxnId,
        LedgerEntryDirection entryDirection,
        LedgerEntryType entryType,
        BigDecimal amountVnd,
        BigDecimal balanceAfterVnd,
        String referenceType,
        UUID referenceId,
        Instant createdAt
) {
}
