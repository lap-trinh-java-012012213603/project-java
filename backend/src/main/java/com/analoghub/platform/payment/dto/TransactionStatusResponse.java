package com.analoghub.platform.payment.dto;

import com.analoghub.platform.payment.domain.EscrowStatus;
import com.analoghub.platform.payment.domain.GatewayProvider;
import com.analoghub.platform.payment.domain.PaymentPurpose;
import com.analoghub.platform.payment.domain.PaymentTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionStatusResponse(
        UUID transactionId,
        String txnCode,
        UUID payerUserId,
        PaymentPurpose paymentPurpose,
        UUID referenceId,
        GatewayProvider gatewayProvider,
        String gatewayTxnNo,
        BigDecimal amountVnd,
        PaymentTransactionStatus status,
        EscrowHoldSummaryDto escrowHold,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt
) {
    public record EscrowHoldSummaryDto(
            UUID escrowId,
            EscrowStatus status,
            BigDecimal grossAmountVnd,
            BigDecimal refundedAmountVnd,
            Instant autoReleaseAt
    ) {
    }
}
