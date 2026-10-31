package com.analoghub.platform.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record EscrowHoldRequest(
        UUID paymentTxnId,
        String referenceType,
        UUID referenceId,
        UUID payerUserId,
        UUID payeeUserId,
        BigDecimal grossAmountVnd
) {
}
