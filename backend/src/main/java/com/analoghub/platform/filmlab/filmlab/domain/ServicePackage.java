package com.analoghub.platform.filmlab.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_packages")
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
    private UUID labId; // Khóa ngoại liên kết tới Lab (Multi-tenant isolation)

    @Column(nullable = false)
    private String name; // Tên gói dịch vụ (Ví dụ: Scan C41 35mm Standard)

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price; // Giá tiền (VND)

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    private ServiceType serviceType; // Loại dịch vụ: DEVELOPMENT, SCANNING, COMBO

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum ServiceType {
        DEVELOPMENT,
        SCANNING,
        COMBO
    }
}