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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pay_escrow_holds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowHold extends BaseAuditEntity {

    @Column(name = "payment_txn_id", nullable = false)
    private UUID paymentTxnId;

    @Column(name = "reference_type", length = 32, nullable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, unique = true)
    private UUID referenceId;

    @Column(name = "payer_user_id", nullable = false)
    private UUID payerUserId;

    @Column(name = "payee_user_id", nullable = false)
    private UUID payeeUserId;

    @Column(name = "gross_amount_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal grossAmountVnd;

    @Builder.Default
    @Column(name = "refunded_amount_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal refundedAmountVnd = new BigDecimal("0.00");

    @Builder.Default
    @Column(name = "commission_fee_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal commissionFeeVnd = new BigDecimal("0.00");

    @Builder.Default
    @Column(name = "net_payout_amount_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal netPayoutAmountVnd = new BigDecimal("0.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private EscrowStatus status;

    @Column(name = "auto_release_at")
    private Instant autoReleaseAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    public BigDecimal getRemainingAmountVnd() {
        BigDecimal gross = grossAmountVnd != null ? grossAmountVnd : BigDecimal.ZERO;
        BigDecimal refunded = refundedAmountVnd != null ? refundedAmountVnd : BigDecimal.ZERO;
        return gross.subtract(refunded).setScale(2, RoundingMode.HALF_UP);
    }
}
