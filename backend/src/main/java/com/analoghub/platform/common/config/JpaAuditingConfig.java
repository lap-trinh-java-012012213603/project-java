package com.analoghub.platform.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {
    private static final String SYSTEM = "system";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () ->
                Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                        .filter(Authentication::isAuthenticated)
                        .filter(auth -> !(auth instanceof AnonymousAuthenticationToken))
                        .map(Authentication::getName)
                        .or(() -> Optional.of(SYSTEM));
    }
}
