package com.analoghub.platform.security;

import com.analoghub.platform.common.exception.AuthException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.TenantAccessDeniedException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityModuleTest {

    private static final String TEST_SECRET = "AnalogHubSuperSecretKeyForJwtSigning2026CapstoneProject999!";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private FilterChain filterChain;

    private JwtProperties jwtProperties;
    private JwtTokenProvider jwtTokenProvider;
    private TokenBlacklistRedis tokenBlacklistRedis;
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private LabTenantGuard labTenantGuard;
    private SecurityExpressions securityExpressions;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TEST_SECRET);
        jwtProperties.setAccessExpirationMs(900_000L);
        jwtProperties.setRefreshExpirationMs(604_800_000L);

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        jwtTokenProvider.init();

        tokenBlacklistRedis = new TokenBlacklistRedis(stringRedisTemplate);
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistRedis);
        labTenantGuard = new LabTenantGuard();
        securityExpressions = new SecurityExpressions(labTenantGuard);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("JwtTokenProvider should encode and decode sub, jti, roles[] and labIds[] claims")
    void shouldGenerateAndParseAccessTokenClaims() {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        UUID lab1 = UUID.randomUUID();
        UUID lab2 = UUID.randomUUID();

        String token = jwtTokenProvider.generateAccessToken(
                userId,
                jti,
                List.of("CUSTOMER", "ROLE_LAB_OWNER"),
                List.of(lab1, lab2)
        );

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.extractJti(token)).isEqualTo(jti);
        assertThat(jwtTokenProvider.extractRoles(token)).containsExactlyInAnyOrder("CUSTOMER", "LAB_OWNER");
        assertThat(jwtTokenProvider.extractLabIds(token)).containsExactlyInAnyOrder(lab1, lab2);
        assertThat(jwtTokenProvider.getRemainingTtl(token)).isPositive();

        AuthenticatedUser principal = jwtTokenProvider.parseAuthenticatedUser(token);
        assertThat(principal.getUserId()).isEqualTo(userId);
        assertThat(principal.getName()).isEqualTo(userId.toString());
        assertThat(principal.getJti()).isEqualTo(jti);
        assertThat(principal.isLabOwner()).isTrue();
        assertThat(principal.isLabMember(lab1)).isTrue();
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_LAB_OWNER");
    }

    @Test
    @DisplayName("JwtTokenProvider should throw ACCESS_TOKEN_EXPIRED when token is expired")
    void shouldThrowExpiredExceptionForExpiredToken() {
        jwtProperties.setAccessExpirationMs(-5_000L);
        String expiredToken = jwtTokenProvider.generateAccessToken(
                UUID.randomUUID(),
                List.of("CUSTOMER"),
                List.of()
        );

        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
        assertThat(jwtTokenProvider.getRemainingTtl(expiredToken)).isEqualTo(Duration.ZERO);
        assertThatThrownBy(() -> jwtTokenProvider.parseClaims(expiredToken))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("TokenBlacklistRedis should store and check auth:blacklist:{jti} in Redis")
    void shouldStoreAndCheckBlacklistedJtiInRedis() {
        String jti = UUID.randomUUID().toString();
        Duration ttl = Duration.ofMinutes(10);
        String expectedKey = "auth:blacklist:" + jti;

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.hasKey(expectedKey)).thenReturn(true);

        tokenBlacklistRedis.blacklistToken(jti, ttl);
        boolean blacklisted = tokenBlacklistRedis.isBlacklisted(jti);

        verify(valueOperations).set(eq(expectedKey), eq("revoked"), eq(ttl));
        assertThat(blacklisted).isTrue();
    }

    @Test
    @DisplayName("JwtAuthenticationFilter should populate SecurityContext when token is valid and not blacklisted")
    void shouldAuthenticateValidNonBlacklistedToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        UUID labId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, jti, List.of("LAB_OWNER"), List.of(labId));

        when(stringRedisTemplate.hasKey("auth:blacklist:" + jti)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/my-labs/" + labId + "/orders");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(userId.toString());
        assertThat(auth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);

        AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(userId);
        assertThat(principal.getJti()).isEqualTo(jti);
        assertThat(principal.getLabIds()).containsExactly(labId);
    }

    @Test
    @DisplayName("JwtAuthenticationFilter should reject blacklisted jti in Redis")
    void shouldRejectBlacklistedToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String token = jwtTokenProvider.generateAccessToken(userId, jti, List.of("CUSTOMER"), List.of());

        when(stringRedisTemplate.hasKey("auth:blacklist:" + jti)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("LabTenantGuard and SecurityExpressions should enforce ADR-04 multi-tenant isolation")
    void shouldEnforceMultiTenantLabAccess() {
        UUID userId = UUID.randomUUID();
        UUID ownLabId = UUID.randomUUID();
        UUID otherLabId = UUID.randomUUID();

        // 1. Unauthenticated context -> AUTH_401
        assertThatThrownBy(() -> labTenantGuard.requireLabAccess(ownLabId))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(securityExpressions.isLabMember(ownLabId)).isFalse();

        // 2. Authenticated as LAB_OWNER of ownLabId
        AuthenticatedUser labOwner = AuthenticatedUser.builder()
                .userId(userId)
                .jti(UUID.randomUUID().toString())
                .roles(Set.of("LAB_OWNER"))
                .labIds(Set.of(ownLabId))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(labOwner, null, labOwner.getAuthorities())
        );

        // Null labId -> TENANT_400
        assertThatThrownBy(() -> labTenantGuard.requireLabAccess(null))
                .isInstanceOf(TenantAccessDeniedException.class)
                .extracting(ex -> ((TenantAccessDeniedException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);

        // Authorized labId -> passes
        labTenantGuard.requireLabAccess(ownLabId);
        assertThat(securityExpressions.isLabMember(ownLabId)).isTrue();
        assertThat(securityExpressions.isLabMember(ownLabId.toString())).isTrue();
        assertThat(securityExpressions.isLabOwner(ownLabId)).isTrue();

        // Other tenant's labId -> TENANT_403 (403 Forbidden)
        assertThatThrownBy(() -> labTenantGuard.requireLabAccess(otherLabId))
                .isInstanceOf(TenantAccessDeniedException.class)
                .extracting(ex -> ((TenantAccessDeniedException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TENANT_ACCESS_DENIED);
        assertThat(securityExpressions.isLabMember(otherLabId)).isFalse();
        assertThat(securityExpressions.isLabMember(otherLabId.toString())).isFalse();
        assertThat(securityExpressions.isLabOwner(otherLabId)).isFalse();

        // Self check
        assertThat(securityExpressions.isSelf(userId)).isTrue();
        assertThat(securityExpressions.isSelf(UUID.randomUUID())).isFalse();
        verify(stringRedisTemplate, never()).hasKey( org.mockito.ArgumentMatchers.anyString());
    }
}
