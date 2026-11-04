package com.analoghub.platform.aiassistant.client;

import lombok.Builder;

@Builder
public record StructuredLlmResponse<T>(
        T data,
        String rawJsonText,
        TokenUsage tokenUsage,
        long latencyMs,
        ModelProvider providerUsed,
        String modelNameUsed
) {
}

