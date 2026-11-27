package com.analoghub.platform.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminLedgerEntryDto(
        UUID ledgerEntryId,
        UUID journalGroupId,
        UUID walletId,
        UUID walletOwnerUserId,
        String walletType,
        UUID paymentTxnId,
        String entryDirection,
        String entryType,
        BigDecimal amountVnd,
        BigDecimal balanceAfterVnd,
        String referenceType,
        UUID referenceId,
        Instant createdAt
) {
}
