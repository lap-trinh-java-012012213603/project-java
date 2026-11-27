package com.analoghub.platform.admin.dto;

import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record RoleApplicationAdminDto(
        UUID applicationId,
        UUID moderationTicketId,
        ApplicantInfo applicant,
        RoleCode requestedRole,
        RoleApplicationStatus status,
        Map<String, Object> portfolioPayload,
        Instant submittedAt,
        UUID reviewedByAdminId,
        String reviewNote,
        Instant reviewedAt
) {

    public record ApplicantInfo(
            UUID userId,
            String email,
            String fullName,
            String phoneNumber,
            Set<RoleCode> currentRoles
    ) {
    }
}
