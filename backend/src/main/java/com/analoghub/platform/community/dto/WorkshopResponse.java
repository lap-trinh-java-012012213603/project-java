package com.analoghub.platform.community.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkshopResponse(
        UUID id,
        UUID organizerUserId,
        UUID hostLabId,
        String workshopType,
        String title,
        String descriptionMarkdown,
        ExpertArticleDetailResponse.AuthorSummary organizer,
        String locationAddress,
        Instant startTime,
        Instant endTime,
        int maxParticipants,
        int currentParticipants,
        int remainingSlots,
        BigDecimal ticketPriceVnd,
        String status,
        Instant createdAt
) {
}
