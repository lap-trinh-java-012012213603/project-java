package com.analoghub.platform.community.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateWorkshopRequest(
        @NotBlank String workshopType,
        UUID hostLabId,
        @NotBlank @Size(min = 10, max = 200) String title,
        @NotBlank @Size(min = 50, max = 10000) String descriptionMarkdown,
        @NotBlank @Size(min = 10, max = 300) String locationAddress,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull @Min(2) @Max(200) Integer maxParticipants,
        @NotNull @DecimalMin("0.00") @DecimalMax("20000000.00") BigDecimal ticketPriceVnd
) {
}
