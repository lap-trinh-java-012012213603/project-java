package com.analoghub.platform.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResolveDisputeResponse(
        UUID disputeId,
        String disputeCode,
        String previousStatus,
        String newStatus,
        FinancialSettlement financialSettlement,
        String orderStatusUpdatedTo,
        UUID resolvedByAdminId,
        UUID auditLogId,
        Instant resolvedAt
) {

    public record FinancialSettlement(
            UUID escrowId,
            String escrowStatusAfter,
            BigDecimal refundedToBuyerVnd,
            BigDecimal grossReleasedToSellerVnd,
            BigDecimal platformCommissionFeeVnd,
            BigDecimal netCreditedToSellerWalletVnd
    ) {
    }
}
