package com.analoghub.platform.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCommentRequest(
        @NotBlank String targetType,
        @NotNull UUID targetId,
        UUID parentCommentId,
        @NotBlank @Size(min = 2, max = 3000) String content
) {
}
