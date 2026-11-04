package com.analoghub.platform.iam.dto;

import java.time.Instant;
import java.util.UUID;

public record UserAddressDto(
        UUID id,
        UUID userId,
        String recipientName,
        String recipientPhone,
        String fullAddress,
        String wardCode,
        String districtCode,
        String provinceCode,
        Double latitude,
        Double longitude,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {

    public UserAddressDto(
            UUID id,
            UUID userId,
            String recipientName,
            String recipientPhone,
            String fullAddress,
            String wardCode,
            String districtCode,
            String provinceCode,
            Double latitude,
            Double longitude,
            boolean isDefault
    ) {
        this(
                id,
                userId,
                recipientName,
                recipientPhone,
                fullAddress,
                wardCode,
                districtCode,
                provinceCode,
                latitude,
                longitude,
                isDefault,
                null,
                null
        );
    }
}
