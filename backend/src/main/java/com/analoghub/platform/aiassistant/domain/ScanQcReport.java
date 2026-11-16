package com.analoghub.platform.aiassistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_scan_qc_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanQcReport {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // Mapped to order_id in V4__init_ai_and_admin_schema.sql (stores archiveFrameId or orderId)
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "scan_image_url", columnDefinition = "TEXT")
    private String scanImageUrl;

    @Column(name = "qc_score", precision = 5, scale = 2)
    private BigDecimal qcScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_issues", columnDefinition = "jsonb")
    private Map<String, Object> detectedIssues;

    @Column(name = "created_at")
    private Instant createdAt;

    // Logical Stage-1 & Stage-2 fields synced into detected_issues JSONB
    @Transient
    private UUID archiveFrameId;

    @Transient
    private UUID archiveRollId;

    @Transient
    private Integer frameNumber;

    @Transient
    private UUID labId;

    @Transient
    private Double laplacianSharpnessScore;

    @Transient
    private Double exposureMeanLuminance;

    @Transient
    private Double darkPixelRatio;

    @Transient
    private Double brightPixelRatio;

    @Transient
    private Integer dustScratchBlobCount;

    @Transient
    private Double colorCastCieScore;

    @Transient
    private Long stage1ExecutionTimeMs;

    @Transient
    private boolean stage2Executed;

    @Transient
    private String visionModelUsed;

    @Transient
    private String detectedDefect;

    @Transient
    private String defectAttribution;

    @Transient
    private Double attributionConfidence;

    @Transient
    private String visionCritiqueVi;

    @Transient
    private String recommendedActionVi;

    @PrePersist
    @PreUpdate
    public void syncFieldsToDetectedIssues() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.orderId == null) {
            this.orderId = this.archiveFrameId != null
                    ? this.archiveFrameId
                    : (this.archiveRollId != null ? this.archiveRollId : UUID.randomUUID());
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }

        Map<String, Object> map = this.detectedIssues != null
                ? new LinkedHashMap<>(this.detectedIssues)
                : new LinkedHashMap<>();

        if (archiveFrameId != null) map.put("archiveFrameId", archiveFrameId.toString());
        if (archiveRollId != null) map.put("archiveRollId", archiveRollId.toString());
        if (frameNumber != null) map.put("frameNumber", frameNumber);
        if (labId != null) map.put("labId", labId.toString());
        if (laplacianSharpnessScore != null) map.put("laplacianSharpnessScore", laplacianSharpnessScore);
        if (exposureMeanLuminance != null) map.put("exposureMeanLuminance", exposureMeanLuminance);
        if (darkPixelRatio != null) map.put("darkPixelRatio", darkPixelRatio);
        if (brightPixelRatio != null) map.put("brightPixelRatio", brightPixelRatio);
        if (dustScratchBlobCount != null) map.put("dustScratchBlobCount", dustScratchBlobCount);
        if (colorCastCieScore != null) map.put("colorCastCieScore", colorCastCieScore);
        if (stage1ExecutionTimeMs != null) map.put("stage1ExecutionTimeMs", stage1ExecutionTimeMs);
        map.put("stage2Executed", stage2Executed);
        if (visionModelUsed != null) map.put("visionModelUsed", visionModelUsed);
        if (detectedDefect != null) map.put("detectedDefect", detectedDefect);
        if (defectAttribution != null) map.put("defectAttribution", defectAttribution);
        if (attributionConfidence != null) map.put("attributionConfidence", attributionConfidence);
        if (visionCritiqueVi != null) map.put("visionCritiqueVi", visionCritiqueVi);
        if (recommendedActionVi != null) map.put("recommendedActionVi", recommendedActionVi);

        this.detectedIssues = map;
    }

    @PostLoad
    public void syncDetectedIssuesToFields() {
        if (detectedIssues == null) {
            return;
        }
        if (archiveFrameId == null && detectedIssues.get("archiveFrameId") instanceof String s) {
            archiveFrameId = parseUuidSafely(s);
        }
        if (archiveRollId == null && detectedIssues.get("archiveRollId") instanceof String s) {
            archiveRollId = parseUuidSafely(s);
        }
        if (frameNumber == null && detectedIssues.get("frameNumber") instanceof Number n) {
            frameNumber = n.intValue();
        }
        if (labId == null && detectedIssues.get("labId") instanceof String s) {
            labId = parseUuidSafely(s);
        }
        if (laplacianSharpnessScore == null && detectedIssues.get("laplacianSharpnessScore") instanceof Number n) {
            laplacianSharpnessScore = n.doubleValue();
        }
        if (exposureMeanLuminance == null && detectedIssues.get("exposureMeanLuminance") instanceof Number n) {
            exposureMeanLuminance = n.doubleValue();
        }
        if (darkPixelRatio == null && detectedIssues.get("darkPixelRatio") instanceof Number n) {
            darkPixelRatio = n.doubleValue();
        }
        if (brightPixelRatio == null && detectedIssues.get("brightPixelRatio") instanceof Number n) {
            brightPixelRatio = n.doubleValue();
        }
        if (dustScratchBlobCount == null && detectedIssues.get("dustScratchBlobCount") instanceof Number n) {
            dustScratchBlobCount = n.intValue();
        }
        if (colorCastCieScore == null && detectedIssues.get("colorCastCieScore") instanceof Number n) {
            colorCastCieScore = n.doubleValue();
        }
        if (stage1ExecutionTimeMs == null && detectedIssues.get("stage1ExecutionTimeMs") instanceof Number n) {
            stage1ExecutionTimeMs = n.longValue();
        }
        if (detectedIssues.get("stage2Executed") instanceof Boolean b) {
            stage2Executed = b;
        }
        if (visionModelUsed == null && detectedIssues.get("visionModelUsed") instanceof String s) {
            visionModelUsed = s;
        }
        if (detectedDefect == null && detectedIssues.get("detectedDefect") instanceof String s) {
            detectedDefect = s;
        }
        if (defectAttribution == null && detectedIssues.get("defectAttribution") instanceof String s) {
            defectAttribution = s;
        }
        if (attributionConfidence == null && detectedIssues.get("attributionConfidence") instanceof Number n) {
            attributionConfidence = n.doubleValue();
        }
        if (visionCritiqueVi == null && detectedIssues.get("visionCritiqueVi") instanceof String s) {
            visionCritiqueVi = s;
        }
        if (recommendedActionVi == null && detectedIssues.get("recommendedActionVi") instanceof String s) {
            recommendedActionVi = s;
        }
    }

    private static UUID parseUuidSafely(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            return null;
        }
    }
}
