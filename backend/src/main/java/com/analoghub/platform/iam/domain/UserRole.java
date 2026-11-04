package com.analoghub.platform.iam.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "iam_user_roles",
        uniqueConstraints = @UniqueConstraint(name = "uk_iam_user_roles_user_role", columnNames = {"user_id", "role_id"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole extends BaseAuditEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "granted_at", nullable = false)
    @Builder.Default
    private Instant grantedAt = Instant.now();

    @PrePersist
    void onPrePersist() {
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }
}
