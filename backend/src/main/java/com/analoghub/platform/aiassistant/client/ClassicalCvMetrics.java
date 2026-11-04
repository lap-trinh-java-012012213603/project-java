package com.analoghub.platform.aiassistant.client;

import lombok.Builder;

@Builder
public record ClassicalCvMetrics(
        double laplacianSharpnessScore,
        double exposureMeanLuminance,
        double darkPixelRatio,
        double brightPixelRatio,
        int dustScratchBlobCount,
        double colorCastCieScore,
        boolean isBlackAndWhiteFilm
) {
}

