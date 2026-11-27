package com.analoghub.platform.admin.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdminAuditLogDto(
        UUID auditLogId,
        ActorSummary actor,
        String actionCode,
        String targetEntityType,
        UUID targetEntityId,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String ipAddress,
        Instant createdAt
) {
    public record ActorSummary(
            UUID userId,
            String email,
            String fullName
    ) {
    }
}
