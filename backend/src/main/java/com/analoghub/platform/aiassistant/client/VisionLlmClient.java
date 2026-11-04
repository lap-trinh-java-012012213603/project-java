package com.analoghub.platform.aiassistant.client;

public interface VisionLlmClient {

    ModelProvider provider();

    VisionDiagnosticResult analyzeScanFrame(
            byte[] compressedWebpImageBytes,
            String mimeType,
            ClassicalCvMetrics cvMetrics,
            String specificPrompt
    );
}

