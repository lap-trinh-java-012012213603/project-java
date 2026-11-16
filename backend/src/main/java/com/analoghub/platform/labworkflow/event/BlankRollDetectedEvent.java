package com.analoghub.platform.labworkflow.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BlankRollDetectedEvent(
        UUID rollId,
        UUID orderId,
        UUID orderItemId,
        UUID labId,
        UUID customerId,
        String rollCode,
        String reason,
        OffsetDateTime detectedAt
) {
}