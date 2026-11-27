package com.analoghub.platform.admin.dto;

import com.analoghub.platform.admin.audit.AuditableResult;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record DecidePayoutResponse(
        UUID payoutRequestId,
        UUID walletId,
        BigDecimal amountVnd,
        String previousStatus,
        String newStatus,
        String bankTransferRef,
        LedgerSettlement ledgerSettlement,
        UUID reviewedByAdminId,
        UUID auditLogId,
        Instant completedAt,
        @JsonIgnore String reviewNote
) implements AuditableResult<DecidePayoutResponse> {

    public record LedgerSettlement(
            UUID journalGroupId,
            String entryType,
            BigDecimal availableBalanceAfterVnd,
            BigDecimal lockedBalanceAfterVnd
    ) {
    }

    @Override
    public UUID auditTargetId() {
        return payoutRequestId;
    }

    @Override
    public Map<String, Object> toAuditOldValues() {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("status", previousStatus);
        oldValues.put("walletId", walletId != null ? walletId.toString() : null);
        oldValues.put("amountVnd", amountVnd);
        return oldValues;
    }

    @Override
    public Map<String, Object> toAuditNewValues() {
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("status", newStatus);
        if (bankTransferRef != null) {
            newValues.put("bankTransferRef", bankTransferRef);
        }
        newValues.put("reviewNote", reviewNote);
        if (ledgerSettlement != null) {
            if (ledgerSettlement.journalGroupId() != null) {
                newValues.put("journalGroupId", ledgerSettlement.journalGroupId().toString());
            }
            newValues.put("entryType", ledgerSettlement.entryType());
            newValues.put("availableBalanceAfterVnd", ledgerSettlement.availableBalanceAfterVnd());
            newValues.put("lockedBalanceAfterVnd", ledgerSettlement.lockedBalanceAfterVnd());
        }
        return newValues;
    }

    @Override
    public DecidePayoutResponse withAuditLogId(UUID newAuditLogId) {
        return new DecidePayoutResponse(
                payoutRequestId,
                walletId,
                amountVnd,
                previousStatus,
                newStatus,
                bankTransferRef,
                ledgerSettlement,
                reviewedByAdminId,
                newAuditLogId,
                completedAt,
                reviewNote
        );
    }
}
