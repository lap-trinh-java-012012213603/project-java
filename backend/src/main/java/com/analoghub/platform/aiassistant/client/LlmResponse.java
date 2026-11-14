package com.analoghub.platform.aiassistant.client;

import lombok.Builder;

@Builder
public record LlmResponse(
        String textContent,
        TokenUsage tokenUsage,
        String finishReason,
        long latencyMs,
        ModelProvider providerUsed,
        String modelNameUsed
) {
}

