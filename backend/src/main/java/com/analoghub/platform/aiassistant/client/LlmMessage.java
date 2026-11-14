package com.analoghub.platform.aiassistant.client;

public record LlmMessage(
        String role,
        String content
) {
}

