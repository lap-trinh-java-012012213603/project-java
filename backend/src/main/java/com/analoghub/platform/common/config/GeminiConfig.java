package com.analoghub.platform.common.config;

import com.google.genai.Client;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.ai.gemini")
public class GeminiConfig {

    private String apiKey;
    private String chatModel;
    private String proModel;
    private String visionModel;
    private String embeddingModel;
    private int embeddingDimensions;
    private float temperature;
    private int maxOutputTokens;

    @Bean
    public Client geminiClient() {
        log.info("Initializing Google GenAI Client with chatModel={}, embeddingModel={}", chatModel, embeddingModel);
        return Client.builder()
                .apiKey(apiKey)
                .build();
    }
}