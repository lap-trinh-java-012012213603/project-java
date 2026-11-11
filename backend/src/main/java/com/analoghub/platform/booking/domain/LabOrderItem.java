package com.analoghub.platform.booking.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bkg_lab_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "service_package_id", nullable = false)
    private UUID servicePackageId;

    @Column(name = "film_stock_id")
    private UUID filmStockId;

    @Column(name = "package_name_snapshot", nullable = false, length = 150)
    private String packageNameSnapshot;

    @Column(name = "film_format", nullable = false, length = 32)
    private String filmFormat;

    @Column(name = "process_type", nullable = false, length = 32)
    private String processType;

    @Builder.Default
    @Column(name = "scan_resolution", nullable = false, length = 32)
    private String scanResolution = "NONE";

    @Column(name = "roll_quantity", nullable = false)
    private int rollQuantity;

    @Column(name = "unit_price_snapshot_vnd", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceSnapshotVnd;

    @Builder.Default
    @Column(name = "push_pull_stops", nullable = false)
    private int pushPullStops = 0;

    @Column(name = "special_instructions", columnDefinition = "TEXT")
    private String specialInstructions;

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