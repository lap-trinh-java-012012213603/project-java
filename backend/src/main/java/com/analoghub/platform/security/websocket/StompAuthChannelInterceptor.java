package com.analoghub.platform.security.websocket;

import com.analoghub.platform.common.exception.AuthException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.TenantAccessDeniedException;
import com.analoghub.platform.common.websocket.WebSocketTopicConstants;
import com.analoghub.platform.security.AuthenticatedUser;
import com.analoghub.platform.security.JwtTokenProvider;
import com.analoghub.platform.security.LabTenantGuard;
import com.analoghub.platform.security.SecurityContextUtils;
import com.analoghub.platform.security.TokenBlacklistRedis;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * STOMP over WebSocket security interceptor enforcing:
 * <ul>
 *     <li>JWT signature, expiry, and Redis blacklist verification on {@code CONNECT}.</li>
 *     <li>Multi-tenant Lab isolation and destination whitelisting (deny-by-default) on {@code SUBSCRIBE} and {@code SEND}.</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class StompAuthChannelInterceptor implements ChannelInterceptor, WebSocketMessageBrokerConfigurer {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ORDER_ACCESS_CACHE_PREFIX = "sec:ws:order:";
    private static final String DELIVERY_ACCESS_CACHE_PREFIX = "sec:ws:dlv:";

    private static final String TOPIC_LAB_KANBAN_PATTERN = "/topic/labs/{labId}/kanban";
    private static final String TOPIC_ORDER_ROLLS_PATTERN = "/topic/orders/{orderId}/rolls";
    private static final String TOPIC_ORDER_STATUS_PATTERN = "/topic/orders/{orderId}";
    private static final String TOPIC_DELIVERY_LOCATION_PATTERN = "/topic/delivery/{taskId}/location";

    private static final Set<String> USER_QUEUE_DESTINATIONS = Set.of(
            WebSocketTopicConstants.USER_QUEUE_MESSAGES,
            WebSocketTopicConstants.USER_QUEUE_NOTIFICATIONS,
            WebSocketTopicConstants.QUEUE_MESSAGES,
            WebSocketTopicConstants.QUEUE_NOTIFICATIONS
    );

    private static final Set<String> ALLOWED_SEND_DESTINATIONS = Set.of(
            WebSocketTopicConstants.APP_CHAT_SEND_FULL,
            WebSocketTopicConstants.APP_CHAT_SEND
    );

    private static final Set<String> ORDER_ELIGIBLE_ROLES = Set.of(
            "CUSTOMER",
            "LAB_OWNER",
            "LAB_STAFF",
            "ADMIN"
    );

    private static final Set<String> DELIVERY_ELIGIBLE_ROLES = Set.of(
            "CUSTOMER",
            "DELIVERY_PARTNER",
            "LAB_OWNER",
            "LAB_STAFF",
            "ADMIN"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Pattern CACHE_TOKEN_DELIMITER = Pattern.compile("[,;:\\s\"\\[\\]{}|]+");
    private static final int UUID_STRING_LENGTH = 36;

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistRedis tokenBlacklistRedis;
    private final LabTenantGuard labTenantGuard;
    private final @Nullable StringRedisTemplate redisTemplate;

    public StompAuthChannelInterceptor(
            JwtTokenProvider jwtTokenProvider,
            TokenBlacklistRedis tokenBlacklistRedis,
            LabTenantGuard labTenantGuard
    ) {
        this(jwtTokenProvider, tokenBlacklistRedis, labTenantGuard, null);
    }

    @Autowired
    public StompAuthChannelInterceptor(
            JwtTokenProvider jwtTokenProvider,
            TokenBlacklistRedis tokenBlacklistRedis,
            LabTenantGuard labTenantGuard,
            @Nullable StringRedisTemplate redisTemplate
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistRedis = tokenBlacklistRedis;
        this.labTenantGuard = labTenantGuard;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        boolean wrappedCopy = false;
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
            wrappedCopy = true;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT -> {
                Authentication authentication = authenticateConnect(accessor);
                if (!accessor.isMutable()) {
                    accessor = StompHeaderAccessor.wrap(message);
                    wrappedCopy = true;
                }
                accessor.setUser(authentication);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (wrappedCopy) {
                    return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
                }
            }
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            default -> {
                // Allow DISCONNECT, UNSUBSCRIBE, ACK, NACK, and heartbeat frames cleanly.
            }
        }

        return message;
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(this);
    }

    private Authentication authenticateConnect(StompHeaderAccessor accessor) {
        String token = extractBearerToken(accessor);
        if (token == null) {
            throw new AuthException(
                    ErrorCode.UNAUTHORIZED,
                    "SEC_WS_AUTH_FAILED: Missing or invalid Authorization Bearer header on STOMP CONNECT"
            );
        }

        AuthenticatedUser authenticatedUser;
        try {
            authenticatedUser = jwtTokenProvider.parseAuthenticatedUser(token);
        } catch (AuthException ex) {
            throw new AuthException(ex.getErrorCode(), "SEC_WS_AUTH_FAILED: " + ex.getMessage());
        }

        String jti = authenticatedUser.getJti();
        if (jti != null && tokenBlacklistRedis.isBlacklisted(jti)) {
            log.warn("[StompAuth] Rejected blacklisted JWT jti={} userId={} on CONNECT",
                    jti, authenticatedUser.getUserId());
            throw new AuthException(
                    ErrorCode.UNAUTHORIZED,
                    "SEC_WS_AUTH_FAILED: JWT token has been revoked (jti=" + jti + ")"
            );
        }

        log.debug("[StompAuth] Authenticated STOMP CONNECT userId={} roles={} labIds={}",
                authenticatedUser.getUserId(), authenticatedUser.getRoles(), authenticatedUser.getLabIds());
        return new UsernamePasswordAuthenticationToken(
                authenticatedUser,
                null,
                authenticatedUser.getAuthorities()
        );
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        ResolvedPrincipal resolved = requireAuthenticatedPrincipal(accessor, "SUBSCRIBE");
        String destination = normalizeDestination(accessor.getDestination());
        if (!StringUtils.hasText(destination)) {
            throw new AccessDeniedException("SEC_WS_DESTINATION_FORBIDDEN: Missing STOMP SUBSCRIBE destination");
        }

        if (USER_QUEUE_DESTINATIONS.contains(destination)) {
            return;
        }

        if (PATH_MATCHER.match(TOPIC_LAB_KANBAN_PATTERN, destination)) {
            Map<String, String> variables = PATH_MATCHER.extractUriTemplateVariables(TOPIC_LAB_KANBAN_PATTERN, destination);
            UUID labId = parseUuid(variables.get("labId"), destination, "labId");
            authorizeLabKanbanSubscription(resolved.authentication(), labId);
            return;
        }

        if (PATH_MATCHER.match(TOPIC_ORDER_ROLLS_PATTERN, destination)) {
            Map<String, String> variables = PATH_MATCHER.extractUriTemplateVariables(TOPIC_ORDER_ROLLS_PATTERN, destination);
            UUID orderId = parseUuid(variables.get("orderId"), destination, "orderId");
            authorizeOrderSubscription(resolved.user(), orderId, destination);
            return;
        }

        if (PATH_MATCHER.match(TOPIC_ORDER_STATUS_PATTERN, destination)) {
            Map<String, String> variables = PATH_MATCHER.extractUriTemplateVariables(TOPIC_ORDER_STATUS_PATTERN, destination);
            UUID orderId = parseUuid(variables.get("orderId"), destination, "orderId");
            authorizeOrderSubscription(resolved.user(), orderId, destination);
            return;
        }

        if (PATH_MATCHER.match(TOPIC_DELIVERY_LOCATION_PATTERN, destination)) {
            Map<String, String> variables = PATH_MATCHER.extractUriTemplateVariables(TOPIC_DELIVERY_LOCATION_PATTERN, destination);
            UUID taskId = parseUuid(variables.get("taskId"), destination, "taskId");
            authorizeDeliverySubscription(resolved.user(), taskId, destination);
            return;
        }

        log.warn("[StompAuth] Denied SUBSCRIBE by user={} to non-whitelisted destination={}",
                resolved.user().getUserId(), destination);
        throw new AccessDeniedException(
                "SEC_WS_DESTINATION_FORBIDDEN: Subscription to destination is not permitted: " + destination
        );
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        ResolvedPrincipal resolved = requireAuthenticatedPrincipal(accessor, "SEND");
        String destination = normalizeDestination(accessor.getDestination());

        if (!StringUtils.hasText(destination) || !ALLOWED_SEND_DESTINATIONS.contains(destination)) {
            log.warn("[StompAuth] Denied SEND by user={} to destination={}",
                    resolved.user().getUserId(), destination);
            throw new AccessDeniedException(
                    "SEC_WS_SEND_DENIED: Sending to destination is not permitted: " + destination
            );
        }
    }

    private void authorizeLabKanbanSubscription(Authentication authentication, UUID labId) {
        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContext scopedContext = SecurityContextHolder.createEmptyContext();
        scopedContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(scopedContext);
        try {
            labTenantGuard.requireLabAccess(labId);
        } catch (TenantAccessDeniedException ex) {
            log.warn("[StompAuth] Tenant isolation violation on Kanban SUBSCRIBE user={} targetLabId={}",
                    authentication.getName(), labId);
            throw ex;
        } finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    private void authorizeOrderSubscription(AuthenticatedUser user, UUID orderId, String destination) {
        authorizeCachedResourceSubscription(
                user,
                orderId,
                ORDER_ACCESS_CACHE_PREFIX,
                "order",
                destination,
                ORDER_ELIGIBLE_ROLES
        );
    }

    private void authorizeDeliverySubscription(AuthenticatedUser user, UUID taskId, String destination) {
        authorizeCachedResourceSubscription(
                user,
                taskId,
                DELIVERY_ACCESS_CACHE_PREFIX,
                "delivery task",
                destination,
                DELIVERY_ELIGIBLE_ROLES
        );
    }

    private void authorizeCachedResourceSubscription(
            AuthenticatedUser user,
            UUID resourceId,
            String cacheKeyPrefix,
            String resourceLabel,
            String destination,
            Set<String> eligibleRoles
    ) {
        if (user.isAdmin()) {
            return;
        }

        if (redisTemplate != null) {
            ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
            String cachedAccess = valueOperations != null ? valueOperations.get(cacheKeyPrefix + resourceId) : null;
            if (StringUtils.hasText(cachedAccess)) {
                if (matchesAllowedPrincipals(user, cachedAccess)) {
                    return;
                }
                log.warn("[StompAuth] Denied SUBSCRIBE by user={} on {} {} (destination={})",
                        user.getUserId(), resourceLabel, resourceId, destination);
                throw new AccessDeniedException(
                        "SEC_WS_SUBSCRIPTION_DENIED: User " + user.getUserId()
                                + " is not authorized for " + resourceLabel + " " + resourceId
                );
            }
        }

        boolean hasEligibleRole = eligibleRoles.stream().anyMatch(user::hasRole);
        if (!hasEligibleRole) {
            log.warn("[StompAuth] Denied SUBSCRIBE by user={} roles={} on destination={}",
                    user.getUserId(), user.getRoles(), destination);
            throw new AccessDeniedException(
                    "SEC_WS_SUBSCRIPTION_DENIED: Insufficient role for destination " + destination
            );
        }
    }

    private boolean matchesAllowedPrincipals(AuthenticatedUser user, String cachedAccess) {
        String[] tokens = CACHE_TOKEN_DELIMITER.split(cachedAccess.trim());
        for (String token : tokens) {
            if (token.length() != UUID_STRING_LENGTH) {
                continue;
            }
            try {
                UUID candidateId = UUID.fromString(token);
                if (candidateId.equals(user.getUserId()) || user.isLabMember(candidateId)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Skip non-UUID segments in serialized cache metadata.
            }
        }
        return false;
    }

    private ResolvedPrincipal requireAuthenticatedPrincipal(StompHeaderAccessor accessor, String commandName) {
        return resolveAuthenticatedPrincipal(accessor)
                .orElseThrow(() -> new AuthException(
                        ErrorCode.UNAUTHORIZED,
                        "SEC_WS_AUTH_FAILED: Unauthenticated STOMP " + commandName + " attempt"
                ));
    }

    private Optional<ResolvedPrincipal> resolveAuthenticatedPrincipal(StompHeaderAccessor accessor) {
        Principal stompUser = accessor.getUser();
        if (stompUser != null) {
            return extractPrincipal(stompUser);
        }
        return SecurityContextUtils.getAuthentication()
                .flatMap(this::extractFromAuthentication);
    }

    private Optional<ResolvedPrincipal> extractPrincipal(Principal principal) {
        if (principal instanceof Authentication authentication) {
            return extractFromAuthentication(authentication);
        }
        if (principal instanceof AuthenticatedUser user) {
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    user.getAuthorities()
            );
            return Optional.of(new ResolvedPrincipal(user, authentication));
        }
        return Optional.empty();
    }

    private Optional<ResolvedPrincipal> extractFromAuthentication(Authentication authentication) {
        if (!authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        Object principalObj = authentication.getPrincipal();
        if (principalObj instanceof AuthenticatedUser user) {
            return Optional.of(new ResolvedPrincipal(user, authentication));
        }
        if (authentication.getDetails() instanceof AuthenticatedUser user) {
            return Optional.of(new ResolvedPrincipal(user, authentication));
        }

        UUID userId = null;
        if (principalObj instanceof UUID uuid) {
            userId = uuid;
        } else if (StringUtils.hasText(authentication.getName())) {
            try {
                userId = UUID.fromString(authentication.getName().trim());
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }

        if (userId == null) {
            return Optional.empty();
        }

        Set<String> roles = authentication.getAuthorities() == null
                ? Set.of()
                : authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());

        AuthenticatedUser reconstructed = AuthenticatedUser.builder()
                .userId(userId)
                .roles(roles)
                .labIds(Set.of())
                .build();
        return Optional.of(new ResolvedPrincipal(reconstructed, authentication));
    }

    private static String extractBearerToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header)) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        if (!StringUtils.hasText(header)) {
            Map<String, List<String>> nativeHeaders = accessor.toNativeHeaderMap();
            for (Map.Entry<String, List<String>> entry : nativeHeaders.entrySet()) {
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(entry.getKey())
                        && entry.getValue() != null
                        && !entry.getValue().isEmpty()) {
                    header = entry.getValue().getFirst();
                    break;
                }
            }
        }
        if (!StringUtils.hasText(header)
                || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String rawToken = header.substring(BEARER_PREFIX.length()).trim();
        return rawToken.isEmpty() ? null : rawToken;
    }

    private static UUID parseUuid(String rawId, String destination, String variableName) {
        if (!StringUtils.hasText(rawId)) {
            throw new AccessDeniedException(
                    "SEC_WS_SUBSCRIPTION_DENIED: Missing " + variableName + " in destination " + destination
            );
        }
        try {
            return UUID.fromString(rawId.trim());
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException(
                    "SEC_WS_SUBSCRIPTION_DENIED: Invalid " + variableName + " in destination " + destination,
                    ex
            );
        }
    }

    private static String normalizeDestination(String destination) {
        return destination == null ? null : destination.trim();
    }

    private record ResolvedPrincipal(AuthenticatedUser user, Authentication authentication) {
    }
}
