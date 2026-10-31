package com.analoghub.platform.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Getter
@Setter
@Configuration
@EnableWebSocketMessageBroker
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private String endpoint;
    private String allowedOrigins;
    private String appDestinationPrefix;
    private String brokerTopicPrefix;
    private String brokerQueuePrefix;
    private String userDestinationPrefix;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = splitCsv(allowedOrigins);

        // 1. Kết nối WebSocket thuần (ws://localhost:8080/ws)
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns(origins);

        // 2. Kết nối kèm SockJS fallback (http://localhost:8080/ws)
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Kênh Server bắn xuống Client:
        // - /topic/orders/{orderId} (Tiến độ tráng cuộn phim)
        // - /user/{userId}/queue/notifications (Thông báo cá nhân / Chat Marketplace)
        registry.enableSimpleBroker(brokerTopicPrefix, brokerQueuePrefix);

        // Tiền tố khi Client gửi message lên Server (@MessageMapping)
        registry.setApplicationDestinationPrefixes(appDestinationPrefix);

        // Tiền tố dành riêng cho từng User
        registry.setUserDestinationPrefix(userDestinationPrefix);
    }

    private String[] splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}