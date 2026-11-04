package com.analoghub.platform.iam.dto;

import java.time.Instant;
import java.util.UUID;

public record DeleteUserAddressResponse(
        UUID deletedAddressId,
        UUID newDefaultAddressId,
        Instant deletedAt
) {
}
