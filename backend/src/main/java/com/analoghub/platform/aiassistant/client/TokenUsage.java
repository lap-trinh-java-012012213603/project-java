package com.analoghub.platform.aiassistant.client;

public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
    public static TokenUsage of(int promptTokens, int completionTokens) {
        return new TokenUsage(
                Math.max(0, promptTokens),
                Math.max(0, completionTokens),
                Math.max(0, promptTokens) + Math.max(0, completionTokens)
        );
    }
}

