package com.analoghub.platform.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .filter(auth -> !(auth instanceof AnonymousAuthenticationToken))
                .flatMap(JpaAuditingConfig::extractUserId);
    }

    private static Optional<UUID> extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(name.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
