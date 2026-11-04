package com.analoghub.platform.security;

import com.analoghub.platform.common.exception.AuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stateless JWT authentication filter that:
 * <ol>
 *     <li>Extracts the {@code Bearer} token from the {@code Authorization} header.</li>
 *     <li>Verifies signature &amp; expiration and parses {@code sub}, {@code jti}, {@code roles[]}, {@code labIds[]}.</li>
 *     <li>Checks Redis key {@code auth:blacklist:{jti}} via {@link TokenBlacklistRedis}.</li>
 *     <li>Populates {@link SecurityContextHolder} with {@link AuthenticatedUser}.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistRedis tokenBlacklistRedis;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (token != null) {
            try {
                AuthenticatedUser authenticatedUser = jwtTokenProvider.parseAuthenticatedUser(token);
                String jti = authenticatedUser.getJti();

                if (jti != null && tokenBlacklistRedis.isBlacklisted(jti)) {
                    log.warn("[JwtAuthFilter] Rejected blacklisted token jti={} userId={} path={}",
                            jti, authenticatedUser.getUserId(), request.getRequestURI());
                    SecurityContextHolder.clearContext();
                } else {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            authenticatedUser,
                            token,
                            authenticatedUser.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (AuthException ex) {
                log.debug("[JwtAuthFilter] Invalid or expired JWT on path={}: {}",
                        request.getRequestURI(), ex.getMessage());
                SecurityContextHolder.clearContext();
            } catch (Exception ex) {
                log.warn("[JwtAuthFilter] Unexpected error while authenticating JWT on path={}: {}",
                        request.getRequestURI(), ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String rawToken = header.substring(BEARER_PREFIX.length()).trim();
            return rawToken.isEmpty() ? null : rawToken;
        }
        return null;
    }
}
