package com.analoghub.platform.platformconfig.dto;

public record UpdateSystemConfigCommand(
        String configKey,
        String configValue,
        String valueType,
        String description
) {
}
