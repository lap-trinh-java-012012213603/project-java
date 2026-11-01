package com.analoghub.platform.iam.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event #2: Published by {@code iam} when a new user account is registered.
 * Consumed by {@code notification} (welcome notification / email, deduplicated by {@code eventId}).
 */
public record UserRegisteredEvent(
        UUID eventId,
        UUID userId,
        String email,
        String fullName,
        Instant registeredAt
) {

    public UserRegisteredEvent {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
    }

    public UserRegisteredEvent(UUID userId, String email, String fullName, Instant registeredAt) {
        this(UUID.randomUUID(), userId, email, fullName, registeredAt);
    }

    public UserRegisteredEvent(UUID userId, String email, String fullName) {
        this(UUID.randomUUID(), userId, email, fullName, Instant.now());
    }
}
