package com.analoghub.platform.aiassistant.client;

public interface LlmClient {

    ModelProvider provider();

    LlmResponse generate(LlmPromptRequest request);

    <T> StructuredLlmResponse<T> generateStructured(LlmPromptRequest request, Class<T> responseClass);
}

