package com.analoghub.platform.delivery.dto;

public record WebhookProcessedResponse(
        String webhookEventId,
        String externalTrackingId,
        String mappedInternalStatus,
        boolean duplicateIgnored
) {
}
