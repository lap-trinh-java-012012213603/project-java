package com.analoghub.platform.community.event;

import java.time.Instant;
import java.util.UUID;

public record ContentReportedEvent(
        UUID eventId,
        UUID reportId,
        String targetType,
        UUID targetId,
        UUID reporterId,
        String reason,
        Instant occurredAt
) {
    public ContentReportedEvent(UUID reportId, String targetType, UUID targetId, UUID reporterId, String reason) {
        this(UUID.randomUUID(), reportId, targetType, targetId, reporterId, reason, Instant.now());
    }
}
