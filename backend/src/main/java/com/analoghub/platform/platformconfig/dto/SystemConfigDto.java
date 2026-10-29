package com.analoghub.platform.platformconfig.dto;

import java.time.Instant;
import java.util.UUID;

public record SystemConfigDto(
        String configKey,
        String configValue,
        String valueType,
        String description,
        Instant updatedAt,
        UUID updatedBy
) {
}
