package com.analoghub.platform.digitalarchive.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateRollMetadataRequest(
        UUID albumId,
        @Size(max = 150) String rollTitle,
        @Size(max = 120) String cameraModel,
        @Size(max = 120) String lensModel,
        @Min(1) Integer shotIso,
        LocalDate shotDate,
        @Size(max = 80) String scanScannerModel
) {}