package com.analoghub.platform.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerReconciliationReportDto(
        boolean balanced,
        BigDecimal totalDebitVnd,
        BigDecimal totalCreditVnd,
        BigDecimal discrepancyVnd,
        int totalLedgerEntries,
        int totalJournalGroups,
        List<UUID> unbalancedJournalGroupIds,
        BigDecimal systemEscrowBalanceVnd,
        BigDecimal systemRevenueBalanceVnd,
        BigDecimal totalUserAvailableBalanceVnd,
        BigDecimal totalUserLockedBalanceVnd,
        int pendingPayoutCount,
        BigDecimal pendingPayoutAmountVnd,
        Instant checkedAt
) {
}
