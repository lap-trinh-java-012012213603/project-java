package com.analoghub.platform.admin.dto;

import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;

import java.time.Instant;
import java.util.UUID;

public record RoleApplicationDecisionResponse(
        UUID applicationId,
        UUID applicantUserId,
        RoleCode requestedRole,
        RoleApplicationStatus previousStatus,
        RoleApplicationStatus newStatus,
        RoleCode grantedRoleCode,
        FilmLabFacade.ProvisionedLabInfo provisionedLab,
        UUID reviewedByAdminId,
        String reviewNote,
        UUID auditLogId,
        Instant decidedAt
) {
}
