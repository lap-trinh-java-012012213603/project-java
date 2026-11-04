package com.analoghub.platform.payment.dto;

import com.analoghub.platform.payment.domain.GatewayProvider;
import com.analoghub.platform.payment.domain.PaymentPurpose;
import com.analoghub.platform.payment.domain.PaymentTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CheckoutResponse(
        UUID transactionId,
        String txnCode,
        PaymentPurpose paymentPurpose,
        UUID referenceId,
        GatewayProvider gatewayProvider,
        BigDecimal amountVnd,
        PaymentTransactionStatus status,
        String checkoutUrl,
        UUID escrowHoldId,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt
) {
}
