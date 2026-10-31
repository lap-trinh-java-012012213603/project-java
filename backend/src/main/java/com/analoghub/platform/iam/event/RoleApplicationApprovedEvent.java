package com.analoghub.platform.iam.event;

import com.analoghub.platform.iam.dto.RoleCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Event #4: Published by {@code iam} when an Admin approves a user's role application via {@code IamFacade}.
 * Consumed by {@code notification} (deduplicated by {@code eventId}).
 */
public record RoleApplicationApprovedEvent(
        UUID eventId,
        UUID applicationId,
        UUID userId,
        String approvedRole,
        UUID reviewedByAdminId,
        Instant approvedAt
) {

    public RoleApplicationApprovedEvent {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (approvedAt == null) {
            approvedAt = Instant.now();
        }
    }

    public RoleApplicationApprovedEvent(UUID applicationId, UUID userId, String approvedRole) {
        this(UUID.randomUUID(), applicationId, userId, approvedRole, null, Instant.now());
    }

    public RoleApplicationApprovedEvent(UUID applicationId, UUID userId, RoleCode approvedRole) {
        this(UUID.randomUUID(), applicationId, userId, approvedRole.name(), null, Instant.now());
    }

    public RoleApplicationApprovedEvent(
            UUID applicationId,
            UUID userId,
            RoleCode approvedRole,
            UUID reviewedByAdminId
    ) {
        this(UUID.randomUUID(), applicationId, userId, approvedRole.name(), reviewedByAdminId, Instant.now());
    }
}
