package com.analoghub.platform.aiassistant.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ScanDiagnosticResponse(
        UUID reportId,
        UUID archiveFrameId,
        UUID archiveRollId,
        int frameNumber,
        UUID labId,
        Stage1ClassicalCvMetricsDto stage1ClassicalCvMetrics,
        Stage2VisionDiagnosticDto stage2VisionDiagnostic,
        double overallQualityScore,
        String updatedArchiveQcStatus,
        Instant analyzedAt
) {

    @Builder
    public record Stage1ClassicalCvMetricsDto(
            double laplacianSharpnessScore,
            double exposureMeanLuminance,
            double darkPixelRatio,
            double brightPixelRatio,
            int dustScratchBlobCount,
            double colorCastCieScore,
            long stage1ExecutionTimeMs
    ) {
    }

    @Builder
    public record Stage2VisionDiagnosticDto(
            boolean executed,
            String visionModelUsed,
            String detectedDefect,
            String defectAttribution,
            double attributionConfidence,
            String visionCritiqueVi,
            String recommendedActionVi
    ) {
    }
}
