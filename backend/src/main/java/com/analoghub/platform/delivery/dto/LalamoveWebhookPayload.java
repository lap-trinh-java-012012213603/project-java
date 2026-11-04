package com.analoghub.platform.delivery.dto;

public record LalamoveWebhookPayload(
        String eventId,
        String eventType,
        Long timestamp,
        OrderInfo order,
        DriverInfo driver
) {
    public record OrderInfo(
            String orderId,
            String status,
            String shareLink
    ) {
    }

    public record DriverInfo(
            String driverId,
            String name,
            String phone,
            String plateNumber,
            LocationInfo location
    ) {
    }

    public record LocationInfo(
            Double lat,
            Double lng
    ) {
    }
}
