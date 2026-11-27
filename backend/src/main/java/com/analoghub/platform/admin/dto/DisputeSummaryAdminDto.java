package com.analoghub.platform.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record DisputeSummaryAdminDto(
        UUID disputeId,
        String disputeCode,
        String disputeType,
        UUID referenceOrderId,
        UUID complainantUserId,
        UUID respondentUserId,
        String status,
        long evidenceCount,
        Instant createdAt,
        Instant updatedAt
) {
}
