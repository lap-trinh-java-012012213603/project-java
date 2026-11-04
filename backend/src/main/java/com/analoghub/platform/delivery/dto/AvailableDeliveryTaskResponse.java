package com.analoghub.platform.delivery.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AvailableDeliveryTaskResponse(
        UUID taskId,
        String referenceType,
        UUID referenceId,
        String direction,
        String provider,
        String status,
        BigDecimal shippingFeeVnd,
        Double distanceToPickupKm,
        Double estimatedRouteDistanceKm,
        Map<String, Object> pickupContactAddress,
        Map<String, Object> dropoffContactAddress,
        Instant pickupDeadlineAt,
        Instant createdAt
) {
}
