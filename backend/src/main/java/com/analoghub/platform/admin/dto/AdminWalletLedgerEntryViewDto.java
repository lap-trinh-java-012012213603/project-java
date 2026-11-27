package com.analoghub.platform.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminWalletLedgerEntryViewDto(
        UUID ledgerEntryId,
        UUID journalGroupId,
        UUID walletId,
        String walletType,
        WalletOwnerSummary owner,
        UUID paymentTxnId,
        String entryDirection,
        String entryType,
        BigDecimal amountVnd,
        BigDecimal balanceAfterVnd,
        String referenceType,
        UUID referenceId,
        Instant createdAt
) {
    public record WalletOwnerSummary(
            UUID userId,
            String fullName,
            String email
    ) {
    }
}
