package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MarketOrderDetailResponse(
        UUID id,
        String orderCode,
        String orderType,
        String status,
        PartyContact buyer,
        PartyContact seller,
        List<MarketOrderCreatedResponse.OrderItemSummary> items,
        FinancialBreakdown financials,
        UUID conversationId,
        Instant inspectionDeadlineAt,
        boolean hasSubmittedReview,
        Instant createdAt,
        Instant updatedAt
) {
    public record PartyContact(
            UUID userId,
            String fullName,
            String phone
    ) {
    }

    public record FinancialBreakdown(
            BigDecimal itemTotalVnd,
            BigDecimal shippingFeeVnd,
            BigDecimal totalPayableVnd,
            String escrowStatus,
            BigDecimal estimatedCommissionFeeVnd,
            BigDecimal estimatedSellerNetPayoutVnd
    ) {
    }
}
