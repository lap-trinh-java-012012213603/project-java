package com.analoghub.platform.marketplace.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ShipMarketOrderRequest(
        @NotNull UUID pickupAddressId,
        @NotEmpty @Size(min = 1, max = 5) List<String> packagingVideoOrPhotoKeys,
        @Size(max = 300) String sellerPickupNote
) {
}
