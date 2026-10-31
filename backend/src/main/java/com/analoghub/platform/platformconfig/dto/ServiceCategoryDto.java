package com.analoghub.platform.platformconfig.dto;

import java.util.UUID;

public record ServiceCategoryDto(
        UUID id,
        String code,
        String name,
        ServiceCategoryGroup categoryGroup,
        String description,
        int displayOrder,
        boolean isActive
) {
}
