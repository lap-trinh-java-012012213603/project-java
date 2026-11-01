package com.analoghub.platform.delivery.dto;

import java.math.BigDecimal;

public record ShippingFeeEstimateDto(
        BigDecimal estimatedFeeVnd,
        double distanceKm,
        String provider
) {
}
