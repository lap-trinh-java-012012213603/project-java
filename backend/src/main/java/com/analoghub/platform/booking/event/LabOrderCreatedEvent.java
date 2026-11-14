package com.analoghub.platform.booking.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LabOrderCreatedEvent(
        UUID orderId,
        String orderCode,
        UUID customerId,
        UUID labId,
        int totalRolls,
        BigDecimal totalAmountVnd,
        OffsetDateTime paymentExpiresAt
) {}