package com.analoghub.platform.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.cors")
public class CorsConfig {

    private static final List<String> REQUIRED_PLATFORM_HEADERS = List.of(
            "Idempotency-Key",
            "X-Idempotency-Key"
    );

    private String allowedOrigins;
    private String allowedMethods;
    private String allowedHeaders;
    private String exposedHeaders;
    private boolean allowCredentials;
    private long maxAge;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(splitCsv(allowedOrigins));
        config.setAllowedMethods(splitCsv(allowedMethods));
        config.setAllowedHeaders(resolveAllowedHeaders(allowedHeaders));
        config.setExposedHeaders(splitCsv(exposedHeaders));
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private List<String> resolveAllowedHeaders(String raw) {
        List<String> configured = splitCsv(raw);
        if (configured.stream().anyMatch("*"::equals)) {
            return configured;
        }
        List<String> merged = new ArrayList<>(configured);
        for (String required : REQUIRED_PLATFORM_HEADERS) {
            boolean exists = merged.stream().anyMatch(h -> h.equalsIgnoreCase(required));
            if (!exists) {
                merged.add(required);
            }
        }
        return List.copyOf(merged);
    }

    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
