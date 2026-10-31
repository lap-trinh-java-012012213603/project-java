package com.analoghub.platform.iam.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String email,
        String phoneNumber,
        String fullName,
        String avatarKey,
        AccountStatus accountStatus,
        AuthProvider authProvider,
        Set<RoleCode> roles,
        Map<String, Object> stylePreferences,
        Instant createdAt
) {
}
