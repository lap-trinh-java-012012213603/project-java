package com.analoghub.platform.booking.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bkg_lab_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_code", nullable = false, unique = true, length = 64)
    private String orderCode;

    @Column(name = "lab_id", nullable = false)
    private UUID labId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING_PAYMENT";

    @Builder.Default
    @Column(name = "fulfillment_in", nullable = false, length = 32)
    private String fulfillmentIn = "STORE_DROP_OFF";

    @Builder.Default
    @Column(name = "fulfillment_out", nullable = false, length = 32)
    private String fulfillmentOut = "DIGITAL_ONLY";

    @Builder.Default
    @Column(name = "negative_handling_policy", nullable = false, length = 32)
    private String negativeHandlingPolicy = "HOLD_30_DAYS";

    @Column(name = "total_rolls", nullable = false)
    private int totalRolls;

    @Builder.Default
    @Column(name = "subtotal_vnd", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotalVnd = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "delivery_fee_vnd", nullable = false, precision = 15, scale = 2)
    private BigDecimal deliveryFeeVnd = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "discount_vnd", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountVnd = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_amount_vnd", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmountVnd = BigDecimal.ZERO;

    @Column(name = "estimated_completion_at")
    private Instant estimatedCompletionAt;

    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}