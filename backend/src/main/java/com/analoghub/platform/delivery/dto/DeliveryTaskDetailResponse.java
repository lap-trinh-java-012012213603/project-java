package com.analoghub.platform.delivery.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DeliveryTaskDetailResponse(
        UUID taskId,
        String referenceType,
        UUID referenceId,
        String direction,
        String provider,
        String externalTrackingId,
        DriverSummary assignedDriver,
        Map<String, Object> pickupContactAddress,
        Map<String, Object> dropoffContactAddress,
        BigDecimal shippingFeeVnd,
        String status,
        String proofOfDeliveryPresignedUrl,
        Instant pickupDeadlineAt,
        LiveLocation latestLiveLocation,
        List<TrackingLogItem> trackingLogs
) {
    public record DriverSummary(
            UUID userId,
            String fullName,
            String phoneNumber,
            String avatarUrl
    ) {
    }

    public record LiveLocation(
            Double latitude,
            Double longitude,
            Instant updatedAt
    ) {
    }

    public record TrackingLogItem(
            UUID logId,
            String status,
            Double latitude,
            Double longitude,
            String note,
            Instant loggedAt
    ) {
    }
}
