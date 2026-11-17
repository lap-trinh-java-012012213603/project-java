package com.analoghub.platform.community.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptAnswerRequest(
        @NotNull UUID commentId
) {
}
