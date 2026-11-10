package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ConfirmOrderReceivedResponse(
        UUID orderId,
        String orderCode,
        String orderStatus,
        EscrowSettlementSummary escrowSettlement,
        Instant completedAt
) {
    public record EscrowSettlementSummary(
            String escrowStatus,
            BigDecimal grossAmountVnd,
            BigDecimal commissionFeeVnd,
            BigDecimal netPayoutToSellerVnd,
            Instant settledAt
    ) {
    }
}
