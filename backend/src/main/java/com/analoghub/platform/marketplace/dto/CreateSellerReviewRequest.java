package com.analoghub.platform.marketplace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSellerReviewRequest(
        @NotNull @Min(1) @Max(5) Short rating,
        @NotNull Boolean itemAsDescribed,
        @NotBlank @Size(min = 10, max = 1000) String comment
) {
}
