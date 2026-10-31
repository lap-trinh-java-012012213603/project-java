package com.analoghub.platform.community.event;

import java.time.Instant;
import java.util.UUID;

public record WorkshopRegisteredEvent(
        UUID eventId,
        UUID registrationId,
        UUID workshopId,
        UUID userId,
        String checkinCode,
        Instant occurredAt
) {
    public WorkshopRegisteredEvent(UUID registrationId, UUID workshopId, UUID userId, String checkinCode) {
        this(UUID.randomUUID(), registrationId, workshopId, userId, checkinCode, Instant.now());
    }
}
