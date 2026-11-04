package com.analoghub.platform.aiassistant.client;

import lombok.Builder;

import java.util.List;

@Builder
public record VisionDiagnosticResult(
        double overallScore,
        String detectedDefect,
        String defectAttribution,
        String visionCritiqueVi,
        List<String> suggestedSolutions,
        double confidenceScore
) {
}

