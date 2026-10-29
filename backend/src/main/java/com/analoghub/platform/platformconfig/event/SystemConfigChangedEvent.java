package com.analoghub.platform.platformconfig.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event #1: Published by {@code platformconfig} when a system configuration or fee rule is updated.
 * Consumed by {@code admin} (audit logging) and Redis cache eviction ({@code DEL cfg:sys:{configKey}}).
 */
public record SystemConfigChangedEvent(
        UUID eventId,
        String configKey,
        String oldValue,
        String newValue,
        UUID changedBy,
        Instant occurredAt
) {

    public SystemConfigChangedEvent {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public SystemConfigChangedEvent(String configKey, String oldValue, String newValue, UUID changedBy) {
        this(UUID.randomUUID(), configKey, oldValue, newValue, changedBy, Instant.now());
    }
}
