package com.analoghub.platform.iam.service;

import com.analoghub.platform.common.exception.AuthException;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.iam.domain.RefreshToken;
import com.analoghub.platform.iam.repository.RefreshTokenRepository;
import com.analoghub.platform.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages Refresh Token family creation, single-use rotation, and replay/reuse detection.
 * <p>
 * Security invariants (Section 1.3 &amp; API 4.4 of {@code 04-iam.md}):
 * <ul>
 *     <li>Raw refresh tokens are never stored in PostgreSQL; only {@code SHA-256} hex hashes are persisted.</li>
 *     <li>Each login session creates a distinct {@code token_family_id}.</li>
 *     <li>Every {@code /api/v1/auth/refresh} call marks the presented token {@code revoked_at = now()} and issues
 *         a new token sharing the same {@code token_family_id}.</li>
 *     <li><b>Reuse Detection</b>: If a token with {@code revoked_at != null} is presented again, the entire
 *         {@code token_family_id} is immediately revoked and {@code auth:user_revoked_after:{userId}} is written to Redis.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenFamilyService {

    public static final String USER_REVOKED_AFTER_KEY_PREFIX = "auth:user_revoked_after:";
    private static final String REFRESH_TOKEN_PREFIX = "rft_";
    private static final int RANDOM_TOKEN_BYTES = 48;
    private static final long DEFAULT_REFRESH_EXPIRATION_MS = 2_592_000_000L; // 30 days
    private static final Duration USER_REVOKED_AFTER_TTL = Duration.ofMinutes(15);
    private static final int MAX_DEVICE_INFO_LENGTH = 255;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public record IssuedRefreshToken(
            String rawRefreshToken,
            RefreshToken tokenEntity,
            long expiresInSeconds
    ) {
    }

    /**
     * Starts a new refresh token family for a newly authenticated device session.
     */
    @Transactional
    public IssuedRefreshToken createNewTokenFamily(UUID userId, String deviceInfo) {
        Objects.requireNonNull(userId, "userId must not be null");
        UUID tokenFamilyId = UUID.randomUUID();
        Instant now = Instant.now();
        return issueTokenInFamily(userId, tokenFamilyId, deviceInfo, now);
    }

    /**
     * Validates an incoming raw Refresh Token, detects replay of already-revoked tokens,
     * and rotates the token within the same {@code token_family_id}.
     * <p>
     * Uses {@code noRollbackFor = {AuthException.class, BusinessException.class}} so that
     * immediate revocation of the entire {@code token_family_id} upon reuse detection is
     * durably committed even though {@link AuthException} is thrown to reject the caller.
     */
    @Transactional(noRollbackFor = {AuthException.class, BusinessException.class})
    public IssuedRefreshToken validateAndRotate(String rawRefreshToken, String deviceInfo) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthException(ErrorCode.REFRESH_TOKEN_EXPIRED, "Refresh Token không được để trống.");
        }

        String incomingHash = hashToken(rawRefreshToken.trim());
        RefreshToken tokenRecord = refreshTokenRepository.findByTokenHashForUpdate(incomingHash)
                .or(() -> refreshTokenRepository.findByTokenHash(incomingHash))
                .orElseThrow(() -> new AuthException(
                        ErrorCode.REFRESH_TOKEN_EXPIRED,
                        "Refresh Token không hợp lệ hoặc không tồn tại."
                ));

        Instant now = Instant.now();

        // 1. REUSE DETECTION: A previously rotated/revoked token is being replayed!
        if (tokenRecord.isRevoked()) {
            log.warn("[Security][ReuseDetection] Replayed revoked RefreshToken id={} familyId={} userId={}. Revoking entire token family!",
                    tokenRecord.getId(), tokenRecord.getTokenFamilyId(), tokenRecord.getUserId());
            revokeTokenFamilyImmediately(tokenRecord.getTokenFamilyId(), tokenRecord.getUserId(), now);
            throw new AuthException(
                    ErrorCode.REFRESH_TOKEN_EXPIRED,
                    "Phát hiện Refresh Token đã thu hồi bị tái sử dụng trái phép. Để bảo vệ tài khoản, toàn bộ phiên đăng nhập thuộc thiết bị này đã bị vô hiệu hóa."
            );
        }

        // 2. Expiration check
        if (tokenRecord.isExpiredAt(now)) {
            tokenRecord.revoke(now);
            refreshTokenRepository.saveAndFlush(tokenRecord);
            throw new AuthException(
                    ErrorCode.REFRESH_TOKEN_EXPIRED,
                    "Refresh Token đã hết hạn. Vui lòng đăng nhập lại."
            );
        }

        // 3. Single-use rotation: revoke current token and issue successor in the same token_family_id
        tokenRecord.revoke(now);
        refreshTokenRepository.saveAndFlush(tokenRecord);

        String resolvedDevice = (deviceInfo != null && !deviceInfo.isBlank())
                ? truncateDeviceInfo(deviceInfo)
                : tokenRecord.getDeviceInfo();

        return issueTokenInFamily(
                tokenRecord.getUserId(),
                tokenRecord.getTokenFamilyId(),
                resolvedDevice,
                now
        );
    }

    /**
     * Immediately revokes all non-revoked refresh tokens belonging to {@code tokenFamilyId}
     * and writes {@code auth:user_revoked_after:{userId}} in Redis.
     */
    @Transactional(noRollbackFor = {AuthException.class, BusinessException.class})
    public void revokeTokenFamilyImmediately(UUID tokenFamilyId, UUID userId, Instant revokedAt) {
        Instant now = revokedAt != null ? revokedAt : Instant.now();
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByTokenFamilyIdAndRevokedAtIsNull(tokenFamilyId);
        if (!activeTokens.isEmpty()) {
            for (RefreshToken active : activeTokens) {
                active.revoke(now);
            }
            refreshTokenRepository.saveAllAndFlush(activeTokens);
        }
        refreshTokenRepository.revokeAllByTokenFamilyId(tokenFamilyId, now);
        recordUserTokensRevokedAfter(userId, now);
    }

    /**
     * Revokes a single refresh token (if owned by {@code userId}) during single-device logout.
     */
    @Transactional
    public void revokeSingleToken(UUID userId, String rawRefreshToken) {
        if (userId == null || rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = hashToken(rawRefreshToken.trim());
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .or(() -> refreshTokenRepository.findByTokenHash(hash));
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            if (userId.equals(token.getUserId()) && !token.isRevoked()) {
                token.revoke(Instant.now());
                refreshTokenRepository.saveAndFlush(token);
            }
        }
    }

    /**
     * Revokes all active refresh tokens across all devices for {@code userId}
     * (used on {@code logoutAllDevices = true}, password change, or account suspension).
     */
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        if (userId == null) {
            return;
        }
        Instant now = Instant.now();
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        if (!activeTokens.isEmpty()) {
            for (RefreshToken token : activeTokens) {
                token.revoke(now);
            }
            refreshTokenRepository.saveAllAndFlush(activeTokens);
        }
        refreshTokenRepository.revokeAllByUserId(userId, now);
        recordUserTokensRevokedAfter(userId, now);
    }

    public long getRefreshExpiresInSeconds() {
        long ms = resolveRefreshExpirationMs();
        return Math.max(1L, ms / 1000L);
    }

    private IssuedRefreshToken issueTokenInFamily(
            UUID userId,
            UUID tokenFamilyId,
            String deviceInfo,
            Instant now
    ) {
        String rawRefreshToken = generateRawRefreshToken(tokenFamilyId);
        String tokenHash = hashToken(rawRefreshToken);
        long expirationMs = resolveRefreshExpirationMs();
        Instant expiresAt = now.plusMillis(expirationMs);

        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .tokenFamilyId(tokenFamilyId)
                .tokenHash(tokenHash)
                .deviceInfo(truncateDeviceInfo(deviceInfo))
                .expiresAt(expiresAt)
                .revokedAt(null)
                .build();

        RefreshToken saved = refreshTokenRepository.saveAndFlush(entity);
        return new IssuedRefreshToken(rawRefreshToken, saved, Math.max(1L, expirationMs / 1000L));
    }

    private long resolveRefreshExpirationMs() {
        long configured = jwtProperties != null ? jwtProperties.getRefreshExpirationMs() : 0L;
        return configured > 0L ? configured : DEFAULT_REFRESH_EXPIRATION_MS;
    }

    private void recordUserTokensRevokedAfter(UUID userId, Instant revokedAt) {
        if (userId == null || stringRedisTemplate == null) {
            return;
        }
        try {
            var ops = stringRedisTemplate.opsForValue();
            if (ops != null) {
                ops.set(
                        USER_REVOKED_AFTER_KEY_PREFIX + userId,
                        String.valueOf(revokedAt.toEpochMilli()),
                        USER_REVOKED_AFTER_TTL
                );
            }
        } catch (Exception ex) {
            log.warn("[RefreshTokenFamily] Failed to write Redis user_revoked_after for userId={}: {}",
                    userId, ex.getMessage());
        }
    }

    private static String generateRawRefreshToken(UUID tokenFamilyId) {
        byte[] randomBytes = new byte[RANDOM_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String compactFamily = tokenFamilyId.toString().replace("-", "").substring(0, 16);
        return REFRESH_TOKEN_PREFIX + compactFamily + "_" + encoded;
    }

    private static String truncateDeviceInfo(String deviceInfo) {
        if (deviceInfo == null || deviceInfo.isBlank()) {
            return null;
        }
        String trimmed = deviceInfo.trim();
        return trimmed.length() <= MAX_DEVICE_INFO_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_DEVICE_INFO_LENGTH);
    }

    public static String hashToken(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("rawToken must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
