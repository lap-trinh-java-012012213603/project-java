package com.analoghub.platform.filmlab.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lab_film_stock_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilmStockCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "brand", nullable = false, length = 80)
    private String brand;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "iso", nullable = false)
    private int iso;

    @Column(name = "film_format", nullable = false, length = 32)
    private String filmFormat;

    @Column(name = "process_type", nullable = false, length = 32)
    private String processType;

    @Column(name = "color_balance", length = 64)
    private String colorBalance;

    @Column(name = "grain_level", length = 32)
    private String grainLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "characteristic_tags", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> characteristicTags = List.of();

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