package com.analoghub.platform.delivery.dto;

public record ShippingFeeEstimateRequest(
        Double pickupLat,
        Double pickupLon,
        Double dropoffLat,
        Double dropoffLon,
        String direction,
        boolean twoWay
) {
}
