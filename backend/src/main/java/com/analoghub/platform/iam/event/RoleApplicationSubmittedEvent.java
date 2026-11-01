package com.analoghub.platform.iam.event;

import com.analoghub.platform.iam.dto.RoleCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Event #3: Published by {@code iam} when a user submits an application to upgrade role
 * ({@code LAB_OWNER}, {@code EXPERT}, {@code DELIVERY_PARTNER}).
 * Consumed by {@code notification} and {@code admin} (upsert moderation/review ticket by {@code applicationId}).
 */
public record RoleApplicationSubmittedEvent(
        UUID eventId,
        UUID applicationId,
        UUID userId,
        String requestedRole,
        Instant submittedAt
) {

    public RoleApplicationSubmittedEvent {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }

    public RoleApplicationSubmittedEvent(UUID applicationId, UUID userId, String requestedRole) {
        this(UUID.randomUUID(), applicationId, userId, requestedRole, Instant.now());
    }

    public RoleApplicationSubmittedEvent(UUID applicationId, UUID userId, RoleCode requestedRole) {
        this(UUID.randomUUID(), applicationId, userId, requestedRole.name(), Instant.now());
    }
}
