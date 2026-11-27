package com.analoghub.platform.labworkflow.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRollStatusRequest(
        @NotBlank String targetStatus,
        String note
) {}