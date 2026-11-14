package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MarketOrderCreatedResponse(
        UUID id,
        String orderCode,
        UUID buyerUserId,
        UUID sellerUserId,
        UUID acceptedOfferId,
        String orderType,
        List<OrderItemSummary> items,
        BigDecimal itemTotalVnd,
        BigDecimal shippingFeeVnd,
        BigDecimal totalPayableVnd,
        String status,
        String paymentPurpose,
        Instant paymentExpiresAt,
        Instant createdAt
) {
    public record OrderItemSummary(
            UUID id,
            UUID listingId,
            String titleSnapshot,
            BigDecimal agreedPriceVnd
    ) {
    }
}
