package com.analoghub.platform.iam.dto;

import java.time.Instant;

public record ChangePasswordResponse(
        Instant passwordChangedAt
) {
}
