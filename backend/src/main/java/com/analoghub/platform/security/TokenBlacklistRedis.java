package com.analoghub.platform.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed JWT Access Token blacklist store ({@code auth:blacklist:{jti}}).
 * <p>
 * When a user logs out or their account is suspended/banned, the token's {@code jti}
 * is written to Redis with a TTL matching the remaining lifetime of the Access Token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistRedis {

    public static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";
    private static final String DEFAULT_REVOKED_VALUE = "revoked";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Blacklists a JWT {@code jti} for its remaining time-to-live.
     */
    public void blacklistToken(String jti, Duration remainingTtl) {
        blacklistToken(jti, remainingTtl, DEFAULT_REVOKED_VALUE);
    }

    /**
     * Blacklists a JWT {@code jti} until the specified expiration instant.
     */
    public void blacklistToken(String jti, Instant expiresAt) {
        if (expiresAt == null) {
            return;
        }
        Duration remainingTtl = Duration.between(Instant.now(), expiresAt);
        blacklistToken(jti, remainingTtl, DEFAULT_REVOKED_VALUE);
    }

    /**
     * Blacklists a JWT {@code jti} with a custom reason for its remaining time-to-live.
     */
    public void blacklistToken(String jti, Duration remainingTtl, String reason) {
        if (jti == null || jti.isBlank() || remainingTtl == null || remainingTtl.isNegative() || remainingTtl.isZero()) {
            return;
        }
        String key = buildKey(jti);
        String value = (reason == null || reason.isBlank()) ? DEFAULT_REVOKED_VALUE : reason.trim();
        stringRedisTemplate.opsForValue().set(key, value, remainingTtl);
        log.debug("[TokenBlacklist] Blacklisted jti={} ttlSeconds={}", jti, remainingTtl.toSeconds());
    }

    /**
     * Checks whether the given JWT {@code jti} is currently blacklisted in Redis ({@code auth:blacklist:{jti}}).
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        String key = buildKey(jti);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * Removes a {@code jti} from the Redis blacklist.
     */
    public void removeFromBlacklist(String jti) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        stringRedisTemplate.delete(buildKey(jti));
    }

    public static String buildKey(String jti) {
        return BLACKLIST_KEY_PREFIX + jti.trim();
    }
}
