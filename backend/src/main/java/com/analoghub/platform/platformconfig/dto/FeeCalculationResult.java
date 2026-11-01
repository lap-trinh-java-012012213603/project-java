package com.analoghub.platform.platformconfig.dto;

import java.math.BigDecimal;

public record FeeCalculationResult(
        FeeScope feeScope,
        BigDecimal grossAmountVnd,
        BigDecimal commissionRate,
        BigDecimal fixedFeeVnd,
        BigDecimal commissionFeeVnd,
        BigDecimal netPayoutAmountVnd
) {
}
