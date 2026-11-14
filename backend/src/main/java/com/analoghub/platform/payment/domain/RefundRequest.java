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
@Table(name = "pay_refund_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest extends BaseAuditEntity {

    @Column(name = "escrow_id", nullable = false)
    private UUID escrowId;

    @Column(name = "refund_reason", length = 255, nullable = false)
    private String refundReason;

    @Column(name = "refund_amount_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal refundAmountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private RefundRequestStatus status;

    @Column(name = "processed_at")
    private Instant processedAt;
}
