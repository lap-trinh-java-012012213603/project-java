package com.analoghub.platform.iam.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Builder
public record AuthUserDto(
        UUID id,
        String email,
        String phoneNumber,
        String fullName,
        String avatarUrl,
        AccountStatus accountStatus,
        AuthProvider authProvider,
        Set<RoleCode> roles,
        List<Map<String, Object>> labMemberships,
        StylePreferencesDto stylePreferences,
        Instant createdAt
) {
}
