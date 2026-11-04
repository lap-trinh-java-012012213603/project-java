package com.analoghub.platform.aiassistant.client;

import lombok.Builder;

import java.util.List;

@Builder
public record LlmPromptRequest(
        String systemInstruction,
        List<LlmMessage> history,
        String userPrompt,
        Double temperature,
        Integer maxOutputTokens,
        String responseSchemaJson,
        String promptKey,
        String promptVersion
) {
}

