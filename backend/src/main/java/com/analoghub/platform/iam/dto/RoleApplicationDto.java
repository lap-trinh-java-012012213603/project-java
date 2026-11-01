package com.analoghub.platform.iam.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RoleApplicationDto(
        UUID id,
        UUID userId,
        RoleCode requestedRole,
        Map<String, Object> portfolioPayload,
        RoleApplicationStatus status,
        UUID reviewedByAdminId,
        String reviewNote,
        Instant reviewedAt,
        Instant createdAt
) {
}
