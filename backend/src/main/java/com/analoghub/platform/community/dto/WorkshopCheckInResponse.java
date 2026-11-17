package com.analoghub.platform.community.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkshopCheckInResponse(
        UUID registrationId,
        UUID workshopId,
        ParticipantSummary participant,
        String checkinCode,
        String status,
        Instant checkedInAt
) {
    public record ParticipantSummary(
            UUID userId,
            String fullName,
            String phone
    ) {
    }
}
