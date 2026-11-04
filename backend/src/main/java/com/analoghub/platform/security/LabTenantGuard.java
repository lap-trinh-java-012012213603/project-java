package com.analoghub.platform.security;

import com.analoghub.platform.common.exception.AuthException;
import com.analoghub.platform.common.exception.TenantAccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Multi-tenant service-level guard for Film Lab operations (ADR-04, Principle 6).
 * <p>
 * Every service method operating on Film Lab resources must invoke {@link #requireLabAccess(UUID)}
 * and bind {@code labId} directly into its repository query ({@code WHERE ... AND lab_id = :labId}).
 */
@Component
public class LabTenantGuard {

    /**
     * Verifies that {@code labId} is non-null and that the currently authenticated user
     * is an authorized member of the target Film Lab tenant.
     *
     * @param labId target Film Lab tenant ID
     * @throws TenantAccessDeniedException if {@code labId} is null ({@code TENANT_400}) or the user does not belong to {@code labId} ({@code TENANT_403})
     * @throws AuthException               if there is no authenticated user in the current security context ({@code AUTH_401})
     */
    public void requireLabAccess(UUID labId) {
        if (labId == null) {
            throw TenantAccessDeniedException.missingContext();
        }

        AuthenticatedUser currentUser = SecurityContextUtils.getCurrentUser()
                .orElseThrow(AuthException::unauthorized);

        if (!currentUser.isLabMember(labId)) {
            throw new TenantAccessDeniedException(labId);
        }
    }

    /**
     * Checks whether the current authenticated user is a member of {@code labId} without throwing an exception.
     */
    public boolean hasLabAccess(UUID labId) {
        if (labId == null) {
            return false;
        }
        return SecurityContextUtils.getCurrentUser()
                .map(user -> user.isLabMember(labId))
                .orElse(false);
    }

    /**
     * Returns the set of {@code labIds} associated with the currently authenticated user.
     */
    public Set<UUID> getCurrentUserLabIds() {
        return SecurityContextUtils.getCurrentUserLabIds();
    }
}
