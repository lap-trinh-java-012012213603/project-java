package com.analoghub.platform.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateMarketOrderRequest(
        @NotNull UUID listingId,
        UUID acceptedOfferId,
        @NotNull UUID shippingAddressId,
        @NotBlank String deliveryProvider,
        @Size(max = 500) String buyerNote
) {
}
