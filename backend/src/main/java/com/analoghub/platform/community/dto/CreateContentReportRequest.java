package com.analoghub.platform.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateContentReportRequest(
        @NotBlank String targetType,
        @NotNull UUID targetId,
        @NotBlank String reasonCode,
        @NotBlank @Size(min = 15, max = 1000) String details
) {
}
