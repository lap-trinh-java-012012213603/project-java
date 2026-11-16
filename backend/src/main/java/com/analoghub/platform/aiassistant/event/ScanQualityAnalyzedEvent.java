package com.analoghub.platform.aiassistant.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ScanQualityAnalyzedEvent(
        UUID eventId,
        UUID archiveRollId,
        UUID archiveFrameId,
        UUID ownerUserId,
        UUID sourceLabId,
        int analyzedFramesCount,
        int stage2EscalatedFramesCount,
        double averageQualityScore,
        String overallQcStatus,
        boolean systematicScannerScratchDetected,
        Instant analyzedAt
) {
}
