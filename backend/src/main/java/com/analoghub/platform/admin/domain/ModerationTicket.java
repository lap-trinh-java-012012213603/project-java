package com.analoghub.platform.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adm_moderation_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    @Column(name = "issue_type", length = 100)
    private String issueType;

    @Builder.Default
    @Column(name = "status", length = 50)
    private String status = "PENDING";

    @Column(name = "assigned_admin_id")
    private UUID assignedAdminId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Transient
    @Builder.Default
    private int reportCount = 1;

    @Transient
    private String resolutionNote;

    public String getTargetType() {
        return entityType;
    }

    public void setTargetType(String targetType) {
        this.entityType = targetType;
    }

    public UUID getTargetId() {
        return entityId;
    }

    public void setTargetId(UUID targetId) {
        this.entityId = targetId;
    }
}
