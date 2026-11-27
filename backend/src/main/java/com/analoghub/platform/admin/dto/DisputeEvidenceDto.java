package com.analoghub.platform.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record DisputeEvidenceDto(
        UUID evidenceId,
        UUID disputeId,
        UUID uploadedByUserId,
        String partyRole,
        String mediaS3Key,
        String presignedViewUrl,
        String description,
        String disputeStatusAfterUpload,
        Instant uploadedAt
) {
}
