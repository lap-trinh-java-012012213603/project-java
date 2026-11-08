package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketOrderSummaryResponse(
        UUID id,
        String orderCode,
        String orderType,
        CounterpartySummary counterparty,
        String primaryItemTitle,
        String primaryItemThumbnailUrl,
        BigDecimal totalPayableVnd,
        String status,
        Instant inspectionDeadlineAt,
        Instant createdAt
) {
    public record CounterpartySummary(
            UUID userId,
            String fullName,
            String avatarUrl
    ) {
    }
}
