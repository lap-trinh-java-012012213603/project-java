package com.analoghub.platform.aiassistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_lab_embeddings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabEmbedding {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "lab_id", nullable = false)
    private UUID labId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Logical vector & metadata fields kept @Transient to strictly match V4__init_ai_and_admin_schema.sql
    @Transient
    private float[] embedding;

    @Transient
    private String slug;

    @Transient
    private String labName;

    @Transient
    private String fullAddress;

    @Transient
    private String provinceCode;

    @Transient
    private List<String> supportedFormats;

    @Transient
    private List<String> supportedProcesses;

    @Transient
    private List<String> scannerModels;

    @Transient
    private BigDecimal minPriceVnd;

    @Transient
    private Integer estimatedTurnaroundHours;

    @Transient
    private Double avgRating;

    @Transient
    private String embeddingModel;

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
