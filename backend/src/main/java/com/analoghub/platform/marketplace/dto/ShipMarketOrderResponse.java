package com.analoghub.platform.marketplace.dto;

import java.time.Instant;
import java.util.UUID;

public record ShipMarketOrderResponse(
        UUID orderId,
        String orderCode,
        String orderStatus,
        DeliveryDispatchSummary deliveryTask,
        Instant updatedAt
) {
    public record DeliveryDispatchSummary(
            UUID taskId,
            String direction,
            String provider,
            String status,
            Instant pickupDeadlineAt
    ) {
    }
}
