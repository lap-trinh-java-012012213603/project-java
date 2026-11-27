package com.analoghub.platform.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkshopCheckInRequest(
        @NotBlank @Size(min = 6, max = 64) String checkinCode
) {
}
