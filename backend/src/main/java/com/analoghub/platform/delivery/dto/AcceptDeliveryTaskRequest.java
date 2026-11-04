package com.analoghub.platform.delivery.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AcceptDeliveryTaskRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double currentLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double currentLongitude,
        @Min(1) @Max(180) Integer estimatedArrivalMinutes
) {
}
