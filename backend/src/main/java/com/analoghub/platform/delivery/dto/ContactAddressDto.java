package com.analoghub.platform.delivery.dto;

public record ContactAddressDto(
        String contactName,
        String contactPhone,
        String fullAddress,
        Double latitude,
        Double longitude
) {
}
