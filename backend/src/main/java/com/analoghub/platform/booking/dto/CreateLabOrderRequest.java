package com.analoghub.platform.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateLabOrderRequest(
        @NotNull UUID labId,
        @NotBlank String fulfillmentIn,
        @NotBlank String fulfillmentOut,
        String negativeHandlingPolicy,
        @NotEmpty @Valid List<OrderItemRequest> items
) {
    public record OrderItemRequest(
            @NotNull UUID servicePackageId,
            UUID filmStockId,
            @Min(1) int rollQuantity,
            int pushPullStops,
            String specialInstructions
    ) {}
}