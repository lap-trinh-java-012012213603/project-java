package com.analoghub.platform.security;

import com.analoghub.platform.common.exception.AuthException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Utility methods for accessing the current {@link AuthenticatedUser} from {@link SecurityContextHolder}.
 */
public final class SecurityContextUtils {

    private SecurityContextUtils() {
    }

    public static Optional<Authentication> getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(auth);
    }

    public static Optional<AuthenticatedUser> getCurrentUser() {
        return getAuthentication().flatMap(SecurityContextUtils::extractAuthenticatedUser);
    }

    public static AuthenticatedUser getRequiredCurrentUser() {
        return getCurrentUser().orElseThrow(AuthException::unauthorized);
    }

    public static Optional<UUID> getCurrentUserId() {
        return getAuthentication().flatMap(auth -> {
            if (auth.getPrincipal() instanceof AuthenticatedUser user) {
                return Optional.of(user.getUserId());
            }
            if (auth.getPrincipal() instanceof UUID uuid) {
                return Optional.of(uuid);
            }
            String name = auth.getName();
            if (name == null || name.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(name.trim()));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        });
    }

    public static UUID getRequiredCurrentUserId() {
        return getCurrentUserId().orElseThrow(AuthException::unauthorized);
    }

    public static Set<UUID> getCurrentUserLabIds() {
        return getCurrentUser()
                .map(AuthenticatedUser::getLabIds)
                .orElse(Set.of());
    }

    private static Optional<AuthenticatedUser> extractAuthenticatedUser(Authentication auth) {
        if (auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        if (auth.getDetails() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        UUID userId = null;
        if (auth.getPrincipal() instanceof UUID uuid) {
            userId = uuid;
        } else if (auth.getName() != null && !auth.getName().isBlank()) {
            try {
                userId = UUID.fromString(auth.getName().trim());
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        if (userId == null) {
            return Optional.empty();
        }
        Set<String> roles = auth.getAuthorities() == null
                ? Set.of()
                : auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
        return Optional.of(AuthenticatedUser.builder()
                .userId(userId)
                .roles(roles)
                .labIds(Set.of())
                .build());
    }
}
