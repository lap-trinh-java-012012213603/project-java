package com.analoghub.platform.platformconfig.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FeeConfigDto(
        UUID id,
        FeeScope feeScope,
        BigDecimal commissionRate,
        BigDecimal fixedFeeVnd,
        BigDecimal minFeeVnd,
        BigDecimal maxFeeVnd,
        boolean isActive,
        Instant updatedAt
) {
}
