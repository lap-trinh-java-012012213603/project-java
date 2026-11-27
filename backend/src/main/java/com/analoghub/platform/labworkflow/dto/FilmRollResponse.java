package com.analoghub.platform.labworkflow.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FilmRollResponse(
        UUID id,
        UUID orderId,
        UUID orderItemId,
        UUID labId,
        UUID customerId,
        String rollCode,
        String filmFormat,
        String processType,
        String scanResolution,
        int pushPullStops,
        String status,
        boolean isBlankRoll,
        OffsetDateTime checkedInAt,
        OffsetDateTime slaDeadlineAt,
        boolean slaBreachNotified,
        OffsetDateTime completedAt
) {}