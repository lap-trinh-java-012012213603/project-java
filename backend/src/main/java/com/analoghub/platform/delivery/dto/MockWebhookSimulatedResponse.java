package com.analoghub.platform.delivery.dto;

import java.util.UUID;

public record MockWebhookSimulatedResponse(
        UUID taskId,
        String previousStatus,
        String currentStatus,
        String publishedDomainEvent,
        boolean duplicateIgnored
) {
}
