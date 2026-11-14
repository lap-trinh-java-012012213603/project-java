package com.analoghub.platform.security;

import com.analoghub.platform.common.exception.AuthException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.TenantAccessDeniedException;
import com.analoghub.platform.security.websocket.StompAuthChannelInterceptor;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
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

    @Nested
    @DisplayName("StompAuthChannelInterceptor WebSocket STOMP Security Tests")
    class StompAuthChannelInterceptorTests {

        @Mock
        private MessageChannel messageChannel;

        private StompAuthChannelInterceptor interceptor;

        @BeforeEach
        void setUpInterceptor() {
            interceptor = new StompAuthChannelInterceptor(
                    jwtTokenProvider,
                    tokenBlacklistRedis,
                    labTenantGuard,
                    stringRedisTemplate
            );
        }

        private Message<byte[]> stompMessage(
                StompCommand command,
                String destination,
                Authentication user,
                String authHeaderName,
                String authHeaderValue
        ) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
            accessor.setLeaveMutable(true);
            if (destination != null) {
                accessor.setDestination(destination);
            }
            if (user != null) {
                accessor.setUser(user);
            }
            if (authHeaderName != null && authHeaderValue != null) {
                accessor.setNativeHeader(authHeaderName, authHeaderValue);
            }
            return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        }

        private UsernamePasswordAuthenticationToken authFor(
                UUID userId,
                Set<String> roles,
                Set<UUID> labIds
        ) {
            AuthenticatedUser principal = AuthenticatedUser.builder()
                    .userId(userId)
                    .jti(UUID.randomUUID().toString())
                    .roles(roles)
                    .labIds(labIds)
                    .build();
            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        }

        @Test
        @DisplayName("CONNECT with valid Bearer JWT authenticates and binds AuthenticatedUser to StompHeaderAccessor")
        void shouldAuthenticateValidJwtOnConnect() {
            UUID userId = UUID.randomUUID();
            String jti = UUID.randomUUID().toString();
            UUID labId = UUID.randomUUID();
            String token = jwtTokenProvider.generateAccessToken(userId, jti, List.of("LAB_OWNER"), List.of(labId));

            when(stringRedisTemplate.hasKey("auth:blacklist:" + jti)).thenReturn(false);

            Message<byte[]> connectMessage = stompMessage(
                    StompCommand.CONNECT,
                    null,
                    null,
                    "Authorization",
                    "Bearer " + token
            );

            Message<?> result = interceptor.preSend(connectMessage, messageChannel);

            StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
            assertThat(resultAccessor).isNotNull();
            assertThat(resultAccessor.getUser()).isInstanceOf(Authentication.class);

            Authentication stompAuth = (Authentication) resultAccessor.getUser();
            assertThat(stompAuth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
            AuthenticatedUser principal = (AuthenticatedUser) stompAuth.getPrincipal();
            assertThat(principal.getUserId()).isEqualTo(userId);
            assertThat(principal.getJti()).isEqualTo(jti);
            assertThat(principal.isLabMember(labId)).isTrue();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }

        @Test
        @DisplayName("CONNECT supports lowercase authorization header")
        void shouldAuthenticateLowercaseAuthorizationHeaderOnConnect() {
            UUID userId = UUID.randomUUID();
            String jti = UUID.randomUUID().toString();
            String token = jwtTokenProvider.generateAccessToken(userId, jti, List.of("CUSTOMER"), List.of());

            when(stringRedisTemplate.hasKey("auth:blacklist:" + jti)).thenReturn(false);

            Message<byte[]> connectMessage = stompMessage(
                    StompCommand.CONNECT,
                    null,
                    null,
                    "authorization",
                    "Bearer " + token
            );

            Message<?> result = interceptor.preSend(connectMessage, messageChannel);
            StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
            assertThat(resultAccessor).isNotNull();
            assertThat(resultAccessor.getUser()).isNotNull();
            assertThat(resultAccessor.getUser().getName()).isEqualTo(userId.toString());
        }

        @Test
        @DisplayName("CONNECT rejects missing, expired, or blacklisted tokens with SEC_WS_AUTH_FAILED")
        void shouldRejectInvalidExpiredOrBlacklistedConnect() {
            // Missing header
            Message<byte[]> missingHeader = stompMessage(StompCommand.CONNECT, null, null, null, null);
            assertThatThrownBy(() -> interceptor.preSend(missingHeader, messageChannel))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("SEC_WS_AUTH_FAILED");

            // Non-Bearer header
            Message<byte[]> basicAuthHeader = stompMessage(
                    StompCommand.CONNECT,
                    null,
                    null,
                    "Authorization",
                    "Basic dXNlcjpwYXNz"
            );
            assertThatThrownBy(() -> interceptor.preSend(basicAuthHeader, messageChannel))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("SEC_WS_AUTH_FAILED");

            // Expired token
            jwtProperties.setAccessExpirationMs(-5_000L);
            String expiredToken = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), List.of("CUSTOMER"), List.of());
            jwtProperties.setAccessExpirationMs(900_000L);
            Message<byte[]> expiredConnect = stompMessage(
                    StompCommand.CONNECT,
                    null,
                    null,
                    "Authorization",
                    "Bearer " + expiredToken
            );
            assertThatThrownBy(() -> interceptor.preSend(expiredConnect, messageChannel))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("SEC_WS_AUTH_FAILED")
                    .extracting(ex -> ((AuthException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_TOKEN_EXPIRED);

            // Blacklisted jti
            String jti = UUID.randomUUID().toString();
            String revokedToken = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), jti, List.of("CUSTOMER"), List.of());
            when(stringRedisTemplate.hasKey("auth:blacklist:" + jti)).thenReturn(true);
            Message<byte[]> blacklistedConnect = stompMessage(
                    StompCommand.CONNECT,
                    null,
                    null,
                    "Authorization",
                    "Bearer " + revokedToken
            );
            assertThatThrownBy(() -> interceptor.preSend(blacklistedConnect, messageChannel))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("SEC_WS_AUTH_FAILED");
        }

        @Test
        @DisplayName("SUBSCRIBE to /topic/labs/{labId}/kanban allows Lab member and immediately disconnects non-member")
        void shouldEnforceLabTenantIsolationOnKanbanSubscribe() {
            UUID ownLabId = UUID.randomUUID();
            UUID foreignLabId = UUID.randomUUID();
            UsernamePasswordAuthenticationToken labMemberAuth = authFor(
                    UUID.randomUUID(),
                    Set.of("LAB_OWNER"),
                    Set.of(ownLabId)
            );

            // Own lab -> permitted
            Message<byte[]> ownLabSubscribe = stompMessage(
                    StompCommand.SUBSCRIBE,
                    "/topic/labs/" + ownLabId + "/kanban",
                    labMemberAuth,
                    null,
                    null
            );
            assertThat(interceptor.preSend(ownLabSubscribe, messageChannel)).isSameAs(ownLabSubscribe);

            // Foreign lab -> rejected immediately with TenantAccessDeniedException
            Message<byte[]> foreignLabSubscribe = stompMessage(
                    StompCommand.SUBSCRIBE,
                    "/topic/labs/" + foreignLabId + "/kanban",
                    labMemberAuth,
                    null,
                    null
            );
            assertThatThrownBy(() -> interceptor.preSend(foreignLabSubscribe, messageChannel))
                    .isInstanceOf(TenantAccessDeniedException.class)
                    .extracting(ex -> ((TenantAccessDeniedException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TENANT_ACCESS_DENIED);

            // Invalid labId -> rejected
            Message<byte[]> malformedLabSubscribe = stompMessage(
                    StompCommand.SUBSCRIBE,
                    "/topic/labs/not-a-uuid/kanban",
                    labMemberAuth,
                    null,
                    null
            );
            assertThatThrownBy(() -> interceptor.preSend(malformedLabSubscribe, messageChannel))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("SEC_WS_SUBSCRIPTION_DENIED");
        }

        @Test
        @DisplayName("SUBSCRIBE to /user/queue/messages and /user/queue/notifications requires authentication")
        void shouldAuthorizePersonalQueuesForAuthenticatedUsersOnly() {
            UsernamePasswordAuthenticationToken customerAuth = authFor(
                    UUID.randomUUID(),
                    Set.of("CUSTOMER"),
                    Set.of()
            );

            Message<byte[]> msgSub = stompMessage(
                    StompCommand.SUBSCRIBE,
                    "/user/queue/messages",
                    customerAuth,
                    null,
                    null
            );
            Message<byte[]> notifSub = stompMessage(
                    StompCommand.SUBSCRIBE,
                    "/user/queue/notifications",
                    customerAuth,
                    null,
                    null
            );
            assertThat(interceptor.preSend(msgSub, messageChannel)).isSameAs(msgSub);
            assertThat(interceptor.preSend(notifSub, messageChannel)).isSameAs(notifSub);

            Message<byte[]> unauthenticatedSub = stompMessage(
                    StompCommand.SUBSCRIBE,
                    "/user/queue/messages",
                    null,
                    null,
                    null
            );
            assertThatThrownBy(() -> interceptor.preSend(unauthenticatedSub, messageChannel))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("SEC_WS_AUTH_FAILED");
        }

        @Test
        @DisplayName("SUBSCRIBE to /topic/orders/{orderId}/rolls and /topic/delivery/{taskId}/location checks Redis cache and role eligibility")
        void shouldAuthorizeOrderAndDeliveryTopicsUsingRedisCacheAndRoleFallback() {
            UUID customerId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            UUID orderLabId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID taskId = UUID.randomUUID();
            UUID driverId = UUID.randomUUID();

            UsernamePasswordAuthenticationToken customerAuth = authFor(customerId, Set.of("CUSTOMER"), Set.of());
            UsernamePasswordAuthenticationToken labStaffAuth = authFor(UUID.randomUUID(), Set.of("LAB_OWNER"), Set.of(orderLabId));
            UsernamePasswordAuthenticationToken outsiderCustomerAuth = authFor(otherUserId, Set.of("CUSTOMER"), Set.of());
            UsernamePasswordAuthenticationToken driverAuth = authFor(driverId, Set.of("DELIVERY_PARTNER"), Set.of());
            UsernamePasswordAuthenticationToken expertAuth = authFor(UUID.randomUUID(), Set.of("EXPERT"), Set.of());
            UsernamePasswordAuthenticationToken adminAuth = authFor(UUID.randomUUID(), Set.of("ADMIN"), Set.of());

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("sec:ws:order:" + orderId)).thenReturn(customerId + ":" + orderLabId);
            when(valueOperations.get("sec:ws:dlv:" + taskId)).thenReturn(customerId + ":" + driverId);

            // Cache hit: customer, owning lab & ADMIN succeed; outsider rejected
            assertThat(interceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/orders/" + orderId + "/rolls", customerAuth, null, null),
                    messageChannel
            )).isNotNull();
            assertThat(interceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/orders/" + orderId, customerAuth, null, null),
                    messageChannel
            )).isNotNull();
            assertThat(interceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/orders/" + orderId + "/rolls", adminAuth, null, null),
                    messageChannel
            )).isNotNull();
            assertThat(interceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/orders/" + orderId + "/rolls", labStaffAuth, null, null),
                    messageChannel
            )).isNotNull();
            assertThatThrownBy(() -> interceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/orders/" + orderId + "/rolls", outsiderCustomerAuth, null, null),
                    messageChannel
            ))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("SEC_WS_SUBSCRIPTION_DENIED");

            // Cache hit on delivery: assigned driver succeeds; unrelated customer rejected
            assertThat(interceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/delivery/" + taskId + "/location", driverAuth, null, null),
                    messageChannel
            )).isNotNull();
            assertThatThrownBy(() -> interceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/delivery/" + taskId + "/location", outsiderCustomerAuth, null, null),
                    messageChannel
            ))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("SEC_WS_SUBSCRIPTION_DENIED");

            // Cache miss fallback: eligible role allowed, ineligible role (EXPERT) denied
            UUID uncachedOrderId = UUID.randomUUID();
            when(valueOperations.get("sec:ws:order:" + uncachedOrderId)).thenReturn(null);
            assertThat(interceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/orders/" + uncachedOrderId + "/rolls", customerAuth, null, null),
                    messageChannel
            )).isNotNull();
            assertThatThrownBy(() -> interceptor.preSend(
                    stompMessage(StompCommand.SUBSCRIBE, "/topic/orders/" + uncachedOrderId + "/rolls", expertAuth, null, null),
                    messageChannel
            ))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("SEC_WS_SUBSCRIPTION_DENIED");
        }

        @Test
        @DisplayName("SUBSCRIBE to unknown destination is denied by default with SEC_WS_DESTINATION_FORBIDDEN")
        void shouldDenyUnknownSubscribeDestinationByDefault() {
            UsernamePasswordAuthenticationToken customerAuth = authFor(
                    UUID.randomUUID(),
                    Set.of("CUSTOMER"),
                    Set.of()
            );

            Message<byte[]> unknownTopic = stompMessage(
                    StompCommand.SUBSCRIBE,
                    "/topic/admin/secrets",
                    customerAuth,
                    null,
                    null
            );

            assertThatThrownBy(() -> interceptor.preSend(unknownTopic, messageChannel))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("SEC_WS_DESTINATION_FORBIDDEN");
        }

        @Test
        @DisplayName("SEND allows /app/chat.send for authenticated users and denies direct broker topic spoofing")
        void shouldAuthorizeSendToChatAppAndDenyBrokerTopicSend() {
            UsernamePasswordAuthenticationToken customerAuth = authFor(
                    UUID.randomUUID(),
                    Set.of("CUSTOMER"),
                    Set.of()
            );

            Message<byte[]> validSend = stompMessage(
                    StompCommand.SEND,
                    "/app/chat.send",
                    customerAuth,
                    null,
                    null
            );
            assertThat(interceptor.preSend(validSend, messageChannel)).isSameAs(validSend);

            Message<byte[]> spoofedTopicSend = stompMessage(
                    StompCommand.SEND,
                    "/topic/labs/" + UUID.randomUUID() + "/kanban",
                    customerAuth,
                    null,
                    null
            );
            assertThatThrownBy(() -> interceptor.preSend(spoofedTopicSend, messageChannel))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("SEC_WS_SEND_DENIED");

            Message<byte[]> unauthSend = stompMessage(
                    StompCommand.SEND,
                    "/app/chat.send",
                    null,
                    null,
                    null
            );
            assertThatThrownBy(() -> interceptor.preSend(unauthSend, messageChannel))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("SEC_WS_AUTH_FAILED");
        }

        @Test
        @DisplayName("configureClientInboundChannel registers StompAuthChannelInterceptor")
        void shouldRegisterInterceptorOnClientInboundChannel() {
            class InspectableChannelRegistration extends ChannelRegistration {
                List<org.springframework.messaging.support.ChannelInterceptor> interceptorsList() {
                    return super.getInterceptors();
                }
            }

            InspectableChannelRegistration registration = new InspectableChannelRegistration();
            interceptor.configureClientInboundChannel(registration);

            assertThat(registration.interceptorsList()).containsExactly(interceptor);
        }
    }
}
