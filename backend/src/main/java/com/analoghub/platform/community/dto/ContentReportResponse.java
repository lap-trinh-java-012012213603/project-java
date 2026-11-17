package com.analoghub.platform.community.dto;

import java.time.Instant;
import java.util.UUID;

public record ContentReportResponse(
        UUID reportId,
        String targetType,
        UUID targetId,
        String reasonCode,
        String status,
        Instant createdAt
) {
}
