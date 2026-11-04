package com.analoghub.platform.delivery.dto;

import java.time.Instant;
import java.util.UUID;

public record DeliveryTaskStatusUpdatedResponse(
        UUID taskId,
        String referenceType,
        UUID referenceId,
        String direction,
        String previousStatus,
        String currentStatus,
        String proofOfDeliveryS3Key,
        String proofOfDeliveryPresignedUrl,
        Instant loggedAt
) {
}
