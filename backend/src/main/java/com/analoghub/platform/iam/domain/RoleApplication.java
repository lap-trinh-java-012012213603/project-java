package com.analoghub.platform.iam.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "iam_role_applications",
        indexes = {
                @Index(name = "idx_iam_role_applications_user_status", columnList = "user_id, status"),
                @Index(name = "idx_iam_role_applications_status", columnList = "status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleApplication extends BaseAuditEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false, length = 50)
    private RoleCode requestedRole;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "portfolio_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> portfolioPayload = new HashMap<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoleApplicationStatus status = RoleApplicationStatus.PENDING;

    @Column(name = "reviewed_by_admin_id")
    private UUID reviewedByAdminId;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
