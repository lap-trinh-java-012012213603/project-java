package com.analoghub.platform.iam.dto;

import java.time.Instant;

public record LogoutResponse(
        String revokedAccessTokenJti,
        boolean logoutAllDevices,
        Instant loggedOutAt
) {
}
