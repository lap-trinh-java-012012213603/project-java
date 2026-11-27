package com.analoghub.platform.admin.dto;

import com.analoghub.platform.admin.audit.AuditableResult;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ModerationTicketResolutionResponse(
        UUID ticketId,
        String targetType,
        UUID targetId,
        String reportedContentSubType,
        String previousTicketStatus,
        String newTicketStatus,
        ModerationAction moderationActionApplied,
        DownstreamEffects downstreamEffects,
        UUID assignedAdminId,
        UUID auditLogId,
        Instant resolvedAt,
        @JsonIgnore String resolutionNote
) implements AuditableResult<ModerationTicketResolutionResponse> {

    public record DownstreamEffects(
            boolean contentHidden,
            boolean removedFromSearchIndex,
            boolean removedFromVectorIndex,
            UUID violatorUserId,
            String newAccountStatus,
            boolean allActiveSessionsRevoked
    ) {
    }

    @Override
    public UUID auditTargetId() {
        return ticketId;
    }

    @Override
    public Map<String, Object> toAuditOldValues() {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("status", previousTicketStatus);
        oldValues.put("reportedContentSubType", reportedContentSubType);
        oldValues.put("targetId", targetId != null ? targetId.toString() : null);
        return oldValues;
    }

    @Override
    public Map<String, Object> toAuditNewValues() {
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("status", newTicketStatus);
        newValues.put("moderationAction", moderationActionApplied != null ? moderationActionApplied.name() : null);
        newValues.put("resolutionNote", resolutionNote);
        if (downstreamEffects != null) {
            newValues.put("contentHidden", downstreamEffects.contentHidden());
            newValues.put("removedFromSearchIndex", downstreamEffects.removedFromSearchIndex());
            newValues.put("removedFromVectorIndex", downstreamEffects.removedFromVectorIndex());
            if (downstreamEffects.violatorUserId() != null) {
                newValues.put("violatorUserId", downstreamEffects.violatorUserId().toString());
            }
            if (downstreamEffects.newAccountStatus() != null) {
                newValues.put("newAccountStatus", downstreamEffects.newAccountStatus());
            }
            newValues.put("allActiveSessionsRevoked", downstreamEffects.allActiveSessionsRevoked());
        }
        return newValues;
    }

    @Override
    public ModerationTicketResolutionResponse withAuditLogId(UUID newAuditLogId) {
        return new ModerationTicketResolutionResponse(
                ticketId,
                targetType,
                targetId,
                reportedContentSubType,
                previousTicketStatus,
                newTicketStatus,
                moderationActionApplied,
                downstreamEffects,
                assignedAdminId,
                newAuditLogId,
                resolvedAt,
                resolutionNote
        );
    }
}
