package com.analoghub.platform.labworkflow.dto;

import jakarta.validation.constraints.NotBlank;

public record QrCheckInRequest(
        @NotBlank String qrTokenOrRollCode,
        String note
) {}