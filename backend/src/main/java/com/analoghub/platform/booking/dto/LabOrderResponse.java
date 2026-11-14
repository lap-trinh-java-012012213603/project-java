package com.analoghub.platform.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LabOrderResponse(
        UUID id,
        String orderCode,
        UUID customerId,
        UUID labId,
        String status,
        String fulfillmentIn,
        String fulfillmentOut,
        String negativeHandlingPolicy,
        int totalRolls,
        BigDecimal subtotalVnd,
        BigDecimal deliveryFeeVnd,
        BigDecimal discountVnd,
        BigDecimal totalAmountVnd,
        OffsetDateTime estimatedCompletionAt,
        OffsetDateTime paymentExpiresAt,
        OffsetDateTime createdAt,
        List<OrderItemDto> items
) {
    public record OrderItemDto(
            UUID id,
            UUID servicePackageId,
            UUID filmStockId,
            String packageNameSnapshot,
            String filmFormat,
            String processType,
            String scanResolution,
            int rollQuantity,
            BigDecimal unitPriceSnapshotVnd,
            int pushPullStops,
            String specialInstructions
    ) {}
}