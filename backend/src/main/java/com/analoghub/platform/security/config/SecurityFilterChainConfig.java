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
<<<<<<< HEAD
            // Tat CSRF cho REST API
            .csrf(csrf -> csrf.disable())
            
            // Dinh tuyen quyen truy cap
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/hello", "/api/v1/common/**", "/ws-analoghub/**").permitAll()
                .anyRequest().authenticated()
            )
            
            // Tat login form va basic auth mac dinh
=======
            // Tắt CSRF bảo vệ mặc định (thường tắt khi làm REST API gọi từ ReactJS)
            .csrf(csrf -> csrf.disable())
            
            // Định tuyến quyền truy cập
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/hello").permitAll() // Mở khóa hoàn toàn cho API này
                .anyRequest().authenticated()              // Các đường dẫn khác vẫn khóa
            )
            
            // Tắt hộp thoại đăng nhập Basic Auth và Form Login mặc định
>>>>>>> f764710a1bf036f55b692c38351864ccd306c790
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}