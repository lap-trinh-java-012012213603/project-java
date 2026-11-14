package com.analoghub.platform.labworkflow.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportBlankRollRequest(
        @NotBlank String note
) {}