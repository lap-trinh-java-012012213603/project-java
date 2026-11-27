package com.analoghub.platform.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DisputeDetailAdminDto(
        UUID disputeId,
        String disputeCode,
        String disputeType,
        UUID referenceOrderId,
        String orderCode,
        String status,
        String claimDescription,
        PartySummary complainant,
        PartySummary respondent,
        EscrowSnapshotInfo escrowSnapshot,
        List<DisputeEvidenceDto> evidences,
        ResolutionSummary resolution
) {

    public record PartySummary(
            UUID userId,
            String fullName,
            String email
    ) {
    }

    public record EscrowSnapshotInfo(
            UUID escrowId,
            String status,
            BigDecimal grossAmountVnd,
            BigDecimal alreadyRefundedAmountVnd,
            BigDecimal disputableRemainingAmountVnd,
            BigDecimal commissionFeeVnd,
            BigDecimal netPayoutAmountVnd
    ) {
    }

    public record ResolutionSummary(
            BigDecimal refundToBuyerVnd,
            BigDecimal releaseToSellerVnd,
            UUID resolvedByAdminId,
            String resolutionNote,
            Instant resolvedAt
    ) {
    }
}
