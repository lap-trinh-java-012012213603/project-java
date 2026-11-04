package com.analoghub.platform.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * HMAC-SHA secret key used to sign and verify JWT Access Tokens.
     */
    private String secret;

    /**
     * Access token time-to-live in milliseconds (default: 15 minutes = 900,000 ms per ADR-04 / Section 2).
     */
    private long accessExpirationMs = 900_000L;

    /**
     * Refresh token time-to-live in milliseconds (default: 7 days = 604,800,000 ms).
     */
    private long refreshExpirationMs = 604_800_000L;
}
