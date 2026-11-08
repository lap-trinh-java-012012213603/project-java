package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UpdateListingResponse(
        UUID id,
        String slug,
        String title,
        BigDecimal priceVnd,
        String conditionGrade,
        String status,
        Long version,
        Instant expiresAt,
        Instant updatedAt
) {
}
