package com.analoghub.platform.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "adm_disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Builder.Default
    @Column(name = "status", length = 50)
    private String status = "AWAITING_RESPONDENT_EVIDENCE";

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Transient
    private String disputeCode;

    @Transient
    private String disputeType;

    @Transient
    private String orderCode;

    @Transient
    private UUID respondentUserId;

    @Transient
    private String reasonCategory;

    @Transient
    private String claimDescription;

    @Transient
    private BigDecimal requestedRefundAmountVnd;

    @Transient
    private UUID attachedAiScanReportId;

    @Transient
    private BigDecimal refundToBuyerVnd;

    @Transient
    private BigDecimal releaseToSellerVnd;

    @Transient
    private UUID resolvedByAdminId;

    @Transient
    private Instant resolvedAt;

    @Transient
    private String resolutionNote;

    public UUID getReferenceOrderId() {
        return orderId;
    }

    public UUID getComplainantUserId() {
        return reporterId;
    }

    @PrePersist
    @PreUpdate
    public void syncSerializedFields() {
        if (hasOpenMetadata()) {
            Map<String, Object> reasonMeta = new LinkedHashMap<>();
            reasonMeta.put("_meta", true);
            putIfNotNull(reasonMeta, "disputeCode", disputeCode);
            putIfNotNull(reasonMeta, "disputeType", disputeType);
            putIfNotNull(reasonMeta, "orderCode", orderCode);
            putIfNotNull(reasonMeta, "respondentUserId", respondentUserId != null ? respondentUserId.toString() : null);
            putIfNotNull(reasonMeta, "reasonCategory", reasonCategory);
            putIfNotNull(reasonMeta, "claimDescription", claimDescription != null ? claimDescription : reason);
            putIfNotNull(reasonMeta, "requestedRefundAmountVnd",
                    requestedRefundAmountVnd != null ? requestedRefundAmountVnd.toPlainString() : null);
            putIfNotNull(reasonMeta, "attachedAiScanReportId",
                    attachedAiScanReportId != null ? attachedAiScanReportId.toString() : null);
            this.reason = JSON_MAPPER.writeValueAsString(reasonMeta);
        } else if (this.reason == null && this.claimDescription != null) {
            this.reason = this.claimDescription;
        }

        if (hasResolutionMetadata()) {
            Map<String, Object> resMeta = new LinkedHashMap<>();
            resMeta.put("_meta", true);
            putIfNotNull(resMeta, "refundToBuyerVnd",
                    refundToBuyerVnd != null ? refundToBuyerVnd.toPlainString() : null);
            putIfNotNull(resMeta, "releaseToSellerVnd",
                    releaseToSellerVnd != null ? releaseToSellerVnd.toPlainString() : null);
            putIfNotNull(resMeta, "resolvedByAdminId",
                    resolvedByAdminId != null ? resolvedByAdminId.toString() : null);
            putIfNotNull(resMeta, "resolvedAt", resolvedAt != null ? resolvedAt.toString() : null);
            putIfNotNull(resMeta, "resolutionNote",
                    resolutionNote != null ? resolutionNote : resolutionNotes);
            this.resolutionNotes = JSON_MAPPER.writeValueAsString(resMeta);
        } else if (this.resolutionNote != null) {
            this.resolutionNotes = this.resolutionNote;
        }
    }

    @PostLoad
    public void hydrateTransientFields() {
        if (reason != null && !reason.isBlank()) {
            Map<String, Object> parsed = tryParseMeta(reason);
            if (parsed != null) {
                this.disputeCode = asString(parsed.get("disputeCode"));
                this.disputeType = asString(parsed.get("disputeType"));
                this.orderCode = asString(parsed.get("orderCode"));
                this.respondentUserId = asUuid(parsed.get("respondentUserId"));
                this.reasonCategory = asString(parsed.get("reasonCategory"));
                this.claimDescription = asString(parsed.get("claimDescription"));
                this.requestedRefundAmountVnd = asBigDecimal(parsed.get("requestedRefundAmountVnd"));
                this.attachedAiScanReportId = asUuid(parsed.get("attachedAiScanReportId"));
            } else if (this.claimDescription == null) {
                this.claimDescription = reason;
            }
        }

        if (resolutionNotes != null && !resolutionNotes.isBlank()) {
            Map<String, Object> parsed = tryParseMeta(resolutionNotes);
            if (parsed != null) {
                this.refundToBuyerVnd = asBigDecimal(parsed.get("refundToBuyerVnd"));
                this.releaseToSellerVnd = asBigDecimal(parsed.get("releaseToSellerVnd"));
                this.resolvedByAdminId = asUuid(parsed.get("resolvedByAdminId"));
                this.resolvedAt = asInstant(parsed.get("resolvedAt"));
                this.resolutionNote = asString(parsed.get("resolutionNote"));
            } else if (this.resolutionNote == null) {
                this.resolutionNote = resolutionNotes;
            }
        }
    }

    private boolean hasOpenMetadata() {
        return disputeCode != null
                || disputeType != null
                || orderCode != null
                || respondentUserId != null
                || reasonCategory != null
                || requestedRefundAmountVnd != null
                || attachedAiScanReportId != null;
    }

    private boolean hasResolutionMetadata() {
        return refundToBuyerVnd != null
                || releaseToSellerVnd != null
                || resolvedByAdminId != null
                || resolvedAt != null;
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object val) {
        if (val != null) {
            map.put(key, val);
        }
    }

    private static Map<String, Object> tryParseMeta(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") || !trimmed.contains("\"_meta\"")) {
            return null;
        }
        try {
            Map<String, Object> parsed = JSON_MAPPER.readValue(trimmed, MAP_TYPE);
            return Boolean.TRUE.equals(parsed.get("_meta")) ? parsed : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String asString(Object raw) {
        return raw != null ? raw.toString() : null;
    }

    private static UUID asUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BigDecimal asBigDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Instant asInstant(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw.toString().trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
