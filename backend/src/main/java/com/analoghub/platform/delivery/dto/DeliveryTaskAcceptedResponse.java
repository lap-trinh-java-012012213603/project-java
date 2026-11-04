package com.analoghub.platform.delivery.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DeliveryTaskAcceptedResponse(
        UUID taskId,
        String externalTrackingId,
        String referenceType,
        UUID referenceId,
        String direction,
        String provider,
        UUID assignedDriverUserId,
        String status,
        BigDecimal shippingFeeVnd,
        Map<String, Object> pickupContactAddress,
        Map<String, Object> dropoffContactAddress,
        Instant pickupDeadlineAt,
        Instant updatedAt
) {
}
