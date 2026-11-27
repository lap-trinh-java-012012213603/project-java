package com.analoghub.platform.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ResolveModerationTicketRequest(
        @NotNull(message = "moderationAction không được để trống")
        ModerationAction moderationAction,

        @Min(value = 1, message = "suspensionDays tối thiểu là 1 ngày")
        @Max(value = 365, message = "suspensionDays tối đa là 365 ngày")
        Integer suspensionDays,

        @NotBlank(message = "resolutionNote không được để trống")
        @Size(min = 10, max = 1000, message = "resolutionNote phải từ 10 đến 1000 ký tự")
        String resolutionNote,

        UUID violatorUserId
) {
    public ResolveModerationTicketRequest(
            ModerationAction moderationAction,
            Integer suspensionDays,
            String resolutionNote
    ) {
        this(moderationAction, suspensionDays, resolutionNote, null);
    }
}
