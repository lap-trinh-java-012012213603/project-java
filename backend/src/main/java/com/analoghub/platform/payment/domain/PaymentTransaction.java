package com.analoghub.platform.payment.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pay_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction extends BaseAuditEntity {

    @Column(name = "txn_code", length = 64, nullable = false, unique = true)
    private String txnCode;

    @Column(name = "payer_user_id", nullable = false)
    private UUID payerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_purpose", length = 32, nullable = false)
    private PaymentPurpose paymentPurpose;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_provider", length = 32, nullable = false)
    private GatewayProvider gatewayProvider;

    @Column(name = "gateway_txn_no", length = 128)
    private String gatewayTxnNo;

    @Column(name = "amount_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private PaymentTransactionStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;
}
