package com.analoghub.platform.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityFilterChainConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Tat CSRF cho REST API
            .csrf(csrf -> csrf.disable())
            
            // Dinh tuyen quyen truy cap
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/hello", "/api/v1/common/**", "/ws-analoghub/**").permitAll()
                .anyRequest().authenticated()
            )
            
            // Tat login form va basic auth mac dinh
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}