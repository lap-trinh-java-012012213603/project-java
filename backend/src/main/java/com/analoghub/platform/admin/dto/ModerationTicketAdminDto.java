package com.analoghub.platform.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModerationTicketAdminDto(
        UUID ticketId,
        String targetType,
        UUID targetId,
        String reportedContentSubType,
        ReportedContentSummary reportedContentSummary,
        int reportCount,
        List<String> topReasonCodes,
        String status,
        UUID assignedAdminId,
        String resolutionNote,
        Instant createdAt,
        Instant updatedAt
) {
    public record ReportedContentSummary(
            String title,
            UUID authorUserId,
            String authorFullName,
            String currentModerationStatus
    ) {
    }
}
