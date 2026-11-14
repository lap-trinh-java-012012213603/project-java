package com.analoghub.platform.security;

import com.analoghub.platform.common.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and verifies stateless JWT Access Tokens according to ADR-04 and Section 2 of {@code adr-decisions.md}.
 * <p>
 * Standard claims included in every Access Token:
 * <ul>
 *     <li>{@code sub}: User ID ({@link UUID})</li>
 *     <li>{@code jti}: Unique token identifier ({@link UUID} string) used for Redis blacklist revocation</li>
 *     <li>{@code roles}: Array of role codes ({@code CUSTOMER}, {@code LAB_OWNER}, {@code EXPERT}, {@code DELIVERY_PARTNER}, {@code ADMIN})</li>
 *     <li>{@code labIds}: Array of Film Lab tenant IDs ({@link UUID} strings) the user is a member of</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_LAB_IDS = "labIds";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final int MIN_HMAC_KEY_BYTES = 32;

    private final JwtProperties jwtProperties;
    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        String rawSecret = jwtProperties.getSecret();
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalStateException("JWT secret (app.jwt.secret / JWT_SECRET) must be configured");
        }
        byte[] keyBytes = rawSecret.trim().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_HMAC_KEY_BYTES) {
            keyBytes = sha256(keyBytes);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a signed JWT Access Token with a newly generated {@code jti}.
     */
    public String generateAccessToken(UUID userId, Collection<String> roles, Collection<UUID> labIds) {
        return generateAccessToken(userId, UUID.randomUUID().toString(), roles, labIds);
    }

    /**
     * Generates a signed JWT Access Token with an explicit {@code jti}.
     */
    public String generateAccessToken(
            UUID userId,
            String jti,
            Collection<String> roles,
            Collection<UUID> labIds
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        String resolvedJti = (jti == null || jti.isBlank()) ? UUID.randomUUID().toString() : jti.trim();

        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtProperties.getAccessExpirationMs());

        List<String> normalizedRoles = normalizeRoleClaims(roles);
        List<String> serializedLabIds = serializeLabIdClaims(labIds);

        return Jwts.builder()
                .id(resolvedJti)
                .subject(userId.toString())
                .claim(CLAIM_ROLES, normalizedRoles)
                .claim(CLAIM_LAB_IDS, serializedLabIds)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Generates an Access Token from an existing {@link AuthenticatedUser}.
     */
    public String generateAccessToken(AuthenticatedUser user) {
        Objects.requireNonNull(user, "user must not be null");
        return generateAccessToken(user.getUserId(), user.getJti(), user.getRoles(), user.getLabIds());
    }

    /**
     * Parses and validates a JWT Access Token, returning its {@link Claims}.
     *
     * @throws AuthException with {@code ACCESS_TOKEN_EXPIRED} if expired, or {@code UNAUTHORIZED} if invalid
     */
    public Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            throw AuthException.unauthorized();
        }
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw AuthException.accessTokenExpired();
        } catch (JwtException | IllegalArgumentException ex) {
            throw AuthException.unauthorized();
        }
    }

    /**
     * Parses a validated JWT Access Token into an {@link AuthenticatedUser} principal.
     */
    public AuthenticatedUser parseAuthenticatedUser(String token) {
        Claims claims = parseClaims(token);

        UUID userId = parseUuidSubject(claims.getSubject());
        String jti = claims.getId();
        Set<String> roles = extractRoles(claims);
        Set<UUID> labIds = extractLabIds(claims);
        Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
        Instant expiresAt = claims.getExpiration() != null ? claims.getExpiration().toInstant() : null;

        return AuthenticatedUser.builder()
                .userId(userId)
                .jti(jti)
                .roles(roles)
                .labIds(labIds)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Checks whether the token has a valid signature, structure, and has not expired.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            parseClaims(token);
            return true;
        } catch (AuthException ex) {
            log.debug("[JWT] Token validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return parseUuidSubject(parseClaims(token).getSubject());
    }

    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    public Set<String> extractRoles(String token) {
        return extractRoles(parseClaims(token));
    }

    public Set<UUID> extractLabIds(String token) {
        return extractLabIds(parseClaims(token));
    }

    public Instant extractExpiration(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return expiration != null ? expiration.toInstant() : Instant.now();
    }

    /**
     * Computes the remaining lifetime of the token until its {@code exp} timestamp.
     * Returns {@link Duration#ZERO} if the token is already expired or invalid.
     */
    public Duration getRemainingTtl(String token) {
        try {
            Instant expiresAt = extractExpiration(token);
            Duration remaining = Duration.between(Instant.now(), expiresAt);
            return remaining.isNegative() ? Duration.ZERO : remaining;
        } catch (AuthException ex) {
            return Duration.ZERO;
        }
    }

    public long getAccessExpirationMs() {
        return jwtProperties.getAccessExpirationMs();
    }

    public long getRefreshExpirationMs() {
        return jwtProperties.getRefreshExpirationMs();
    }

    private SecretKey getSigningKey() {
        if (this.signingKey == null) {
            init();
        }
        return this.signingKey;
    }

    private static UUID parseUuidSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw AuthException.unauthorized();
        }
        try {
            return UUID.fromString(subject.trim());
        } catch (IllegalArgumentException ex) {
            throw AuthException.unauthorized();
        }
    }

    private static List<String> normalizeRoleClaims(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .map(s -> s.startsWith(ROLE_PREFIX) ? s.substring(ROLE_PREFIX.length()) : s)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private static List<String> serializeLabIdClaims(Collection<UUID> labIds) {
        if (labIds == null || labIds.isEmpty()) {
            return List.of();
        }
        return labIds.stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .toList();
    }

    private static Set<String> extractRoles(Claims claims) {
        Object rawRoles = claims.get(CLAIM_ROLES);
        if (!(rawRoles instanceof Collection<?> collection)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : collection) {
            if (item != null) {
                String role = item.toString().trim().toUpperCase(Locale.ROOT);
                if (role.startsWith(ROLE_PREFIX)) {
                    role = role.substring(ROLE_PREFIX.length());
                }
                if (!role.isEmpty()) {
                    result.add(role);
                }
            }
        }
        return Set.copyOf(result);
    }

    private static Set<UUID> extractLabIds(Claims claims) {
        Object rawLabIds = claims.get(CLAIM_LAB_IDS);
        if (!(rawLabIds instanceof Collection<?> collection)) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (Object item : collection) {
            if (item instanceof UUID uuid) {
                result.add(uuid);
            } else if (item != null) {
                String str = item.toString().trim();
                if (!str.isEmpty()) {
                    try {
                        result.add(UUID.fromString(str));
                    } catch (IllegalArgumentException ex) {
                        log.warn("[JWT] Ignoring malformed labId claim value: {}", str);
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
