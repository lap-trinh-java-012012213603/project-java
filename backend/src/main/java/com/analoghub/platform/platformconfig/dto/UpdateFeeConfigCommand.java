package com.analoghub.platform.platformconfig.dto;

import java.math.BigDecimal;

public record UpdateFeeConfigCommand(
        FeeScope feeScope,
        BigDecimal commissionRate,
        BigDecimal fixedFeeVnd,
        BigDecimal minFeeVnd,
        BigDecimal maxFeeVnd,
        Boolean isActive
) {
}
