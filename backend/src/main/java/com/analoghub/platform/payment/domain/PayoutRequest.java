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
@Table(name = "pay_payout_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequest extends BaseAuditEntity {

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Column(name = "amount_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal amountVnd;

    @Column(name = "bank_code", length = 32, nullable = false)
    private String bankCode;

    @Column(name = "bank_account_no", length = 64, nullable = false)
    private String bankAccountNo;

    @Column(name = "bank_account_name", length = 128, nullable = false)
    private String bankAccountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private PayoutRequestStatus status;

    @Column(name = "bank_transfer_ref", length = 128)
    private String bankTransferRef;

    @Column(name = "completed_at")
    private Instant completedAt;
}
