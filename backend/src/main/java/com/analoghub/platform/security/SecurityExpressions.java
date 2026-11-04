package com.analoghub.platform.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SpEL security expressions bean registered as {@code @Component("securityExpressions")}
 * for method-level and WebSocket topic-level authorization (ADR-04, Principle 6).
 * <p>
 * Usage example:
 * <pre>{@code
 * @PreAuthorize("@securityExpressions.isLabMember(#labId)")
 * }</pre>
 */
@Component("securityExpressions")
@RequiredArgsConstructor
public class SecurityExpressions {

    private final LabTenantGuard labTenantGuard;

    /**
     * Returns {@code true} if the current authenticated user is a member of the specified Film Lab ({@code labIds[]} contains {@code labId}).
     */
    public boolean isLabMember(UUID labId) {
        return labTenantGuard.hasLabAccess(labId);
    }

    /**
     * String overload for SpEL expressions or STOMP destination variables where {@code labId} is passed as a {@link String}.
     */
    public boolean isLabMember(String labId) {
        UUID parsed = parseUuidOrNull(labId);
        return parsed != null && isLabMember(parsed);
    }

    /**
     * Returns {@code true} if the current authenticated user holds the {@code LAB_OWNER} role and belongs to {@code labId}.
     */
    public boolean isLabOwner(UUID labId) {
        if (labId == null) {
            return false;
        }
        return SecurityContextUtils.getCurrentUser()
                .map(user -> user.isLabOwner() && user.isLabMember(labId))
                .orElse(false);
    }

    public boolean isLabOwner(String labId) {
        UUID parsed = parseUuidOrNull(labId);
        return parsed != null && isLabOwner(parsed);
    }

    /**
     * Returns {@code true} if the current authenticated user is either a member of {@code labId} or a platform {@code ADMIN}.
     */
    public boolean hasLabAccess(UUID labId) {
        if (labId == null) {
            return false;
        }
        return SecurityContextUtils.getCurrentUser()
                .map(user -> user.isLabMember(labId) || user.isAdmin())
                .orElse(false);
    }

    public boolean hasLabAccess(String labId) {
        UUID parsed = parseUuidOrNull(labId);
        return parsed != null && hasLabAccess(parsed);
    }

    /**
     * Returns {@code true} if the current authenticated user's ID matches {@code userId}.
     */
    public boolean isSelf(UUID userId) {
        if (userId == null) {
            return false;
        }
        return SecurityContextUtils.getCurrentUserId()
                .map(userId::equals)
                .orElse(false);
    }

    public boolean isSelf(String userId) {
        UUID parsed = parseUuidOrNull(userId);
        return parsed != null && isSelf(parsed);
    }

    public boolean isAdmin() {
        return SecurityContextUtils.getCurrentUser()
                .map(AuthenticatedUser::isAdmin)
                .orElse(false);
    }

    private static UUID parseUuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
