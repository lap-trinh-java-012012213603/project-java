package com.analoghub.platform.security;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Immutable security principal populated from a validated JWT Access Token.
 * Carries {@code sub (userId)}, {@code jti}, {@code roles[]}, and multi-tenant {@code labIds[]}.
 */
@Getter
public class AuthenticatedUser implements UserDetails, Principal {

    private static final String ROLE_PREFIX = "ROLE_";

    private final UUID userId;
    private final String jti;
    private final Set<String> roles;
    private final Set<UUID> labIds;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Set<GrantedAuthority> authorities;

    @Builder
    public AuthenticatedUser(
            UUID userId,
            String jti,
            Collection<String> roles,
            Collection<UUID> labIds,
            Instant issuedAt,
            Instant expiresAt
    ) {
        this.userId = Objects.requireNonNull(userId, "userId (sub) must not be null");
        this.jti = jti;
        this.roles = normalizeRoles(roles);
        this.labIds = labIds == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(labIds));
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.authorities = this.roles.stream()
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), Collections::unmodifiableSet));
    }

    private static Set<String> normalizeRoles(Collection<String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : rawRoles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String cleaned = role.trim().toUpperCase(Locale.ROOT);
            if (cleaned.startsWith(ROLE_PREFIX)) {
                cleaned = cleaned.substring(ROLE_PREFIX.length());
            }
            if (!cleaned.isEmpty()) {
                normalized.add(cleaned);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    public boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String cleaned = role.trim().toUpperCase(Locale.ROOT);
        if (cleaned.startsWith(ROLE_PREFIX)) {
            cleaned = cleaned.substring(ROLE_PREFIX.length());
        }
        return roles.contains(cleaned);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isLabOwner() {
        return hasRole("LAB_OWNER");
    }

    public boolean isLabMember(UUID labId) {
        return labId != null && labIds.contains(labId);
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    @Override
    public String getUsername() {
        return userId.toString();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
