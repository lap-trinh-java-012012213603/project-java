package com.analoghub.platform.delivery.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "dlv_tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryTask extends BaseAuditEntity {

    @Column(name = "reference_type", length = 32, nullable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 32, nullable = false)
    private DeliveryDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 32, nullable = false)
    private LogisticsProviderType provider;

    @Column(name = "external_tracking_id", length = 128, unique = true)
    private String externalTrackingId;

    @Column(name = "assigned_driver_user_id")
    private UUID assignedDriverUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pickup_contact_address", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> pickupContactAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dropoff_contact_address", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> dropoffContactAddress;

    @Column(name = "shipping_fee_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal shippingFeeVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private DeliveryTaskStatus status;

    @Column(name = "proof_of_delivery_s3_key", length = 512)
    private String proofOfDeliveryS3Key;

    @Column(name = "pickup_deadline_at")
    private Instant pickupDeadlineAt;
}
