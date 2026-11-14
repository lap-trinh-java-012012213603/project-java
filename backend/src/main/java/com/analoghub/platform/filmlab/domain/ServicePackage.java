package com.analoghub.platform.filmlab.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lab_service_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lab_id", nullable = false)
    private UUID labId;

    @Column(name = "category_code", nullable = false, length = 64)
    private String categoryCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "film_format", nullable = false, length = 32)
    private String filmFormat;

    @Column(name = "process_type", nullable = false, length = 32)
    private String processType;

    @Builder.Default
    @Column(name = "scan_resolution", nullable = false, length = 32)
    private String scanResolution = "NONE";

    @Column(name = "unit_price_vnd", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceVnd;

    @Builder.Default
    @Column(name = "estimated_hours", nullable = false)
    private int estimatedHours = 24;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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

    // Helper method for Soft Delete
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}