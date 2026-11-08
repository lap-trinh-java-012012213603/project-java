package com.analoghub.platform.marketplace.dto;

import jakarta.validation.constraints.Size;

public record TechnicalSpecsDto(
        @Size(max = 120) String shutterSpeedAccuracy,
        @Size(max = 120) String lightMeterStatus,
        @Size(max = 200) String opticsCondition,
        @Size(max = 120) String lightSealCondition,
        @Size(max = 60) String mountType,
        @Size(max = 80) String serialNumber
) {
}
