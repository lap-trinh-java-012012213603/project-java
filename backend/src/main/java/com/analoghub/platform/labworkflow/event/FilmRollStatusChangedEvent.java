package com.analoghub.platform.labworkflow.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FilmRollStatusChangedEvent(
        UUID rollId,
        UUID orderId,
        UUID labId,
        UUID customerId,
        String rollCode,
        String fromStatus,
        String toStatus,
        OffsetDateTime slaDeadlineAt,
        OffsetDateTime changedAt
) {}