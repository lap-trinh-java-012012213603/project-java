package com.analoghub.platform.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderRequest(
        @NotBlank String reason
) {}