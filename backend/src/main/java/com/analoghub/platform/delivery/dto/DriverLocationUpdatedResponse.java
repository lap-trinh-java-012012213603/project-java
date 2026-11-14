package com.analoghub.platform.delivery.dto;

import java.time.Instant;
import java.util.UUID;

public record DriverLocationUpdatedResponse(
        UUID taskId,
        String status,
        Double latitude,
        Double longitude,
        Double headingDegrees,
        Double speedKmh,
        boolean persistedToDb,
        Instant broadcastedAt
) {
}
