package com.analoghub.platform.iam.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token entity stored in {@code iam_refresh_tokens}.
 * <p>
 * Only the SHA-256 hex digest ({@code token_hash}) of the raw refresh token is persisted.
 * Tokens belonging to the same login session share {@code token_family_id} for single-use
 * rotation and replay/reuse detection.
 */
@Entity
@Table(name = "iam_refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends BaseAuditEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_family_id", nullable = false)
    private UUID tokenFamilyId;

    @Column(name = "token_hash", length = 128, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return isExpiredAt(Instant.now());
    }

    public boolean isExpiredAt(Instant referenceInstant) {
        return expiresAt != null && expiresAt.isBefore(referenceInstant);
    }

    public void revoke(Instant when) {
        this.revokedAt = when != null ? when : Instant.now();
    }
}
