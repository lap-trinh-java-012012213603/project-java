package com.analoghub.platform.delivery.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateDeliveryTaskRequest(
        String referenceType,
        UUID referenceId,
        String direction,
        String preferredProvider,
        ContactAddressDto pickupAddress,
        ContactAddressDto dropoffAddress,
        Instant pickupDeadlineAt
) {
}
