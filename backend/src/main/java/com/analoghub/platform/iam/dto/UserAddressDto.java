package com.analoghub.platform.iam.dto;

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
        boolean isDefault
) {
}
