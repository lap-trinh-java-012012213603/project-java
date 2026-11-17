package com.analoghub.platform.community.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkshopRegistrationResponse(
        UUID registrationId,
        UUID workshopId,
        UUID userId,
        String status,
        BigDecimal ticketPriceVnd,
        String paymentPurpose,
        String checkinCode,
        Instant createdAt
) {
}
