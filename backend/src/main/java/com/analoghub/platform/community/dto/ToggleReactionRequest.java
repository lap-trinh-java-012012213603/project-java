package com.analoghub.platform.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ToggleReactionRequest(
        @NotBlank String targetType,
        @NotNull UUID targetId,
        @NotBlank String reactionType,
        @NotNull Boolean active
) {
}
