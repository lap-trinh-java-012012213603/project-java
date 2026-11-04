package com.analoghub.platform.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MockLogisticsWebhookRequest(
        @NotBlank @Size(max = 100) String eventId,
        @NotNull UUID taskId,
        @NotBlank String simulatedStatus,
        Double latitude,
        Double longitude,
        String proofOfDeliveryS3Key,
        @Size(max = 300) String note
) {
}
