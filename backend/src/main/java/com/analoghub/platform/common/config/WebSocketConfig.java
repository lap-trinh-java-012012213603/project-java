package com.analoghub.platform.common.config;

<<<<<<< HEAD
import com.analoghub.platform.common.websocket.WebSocketTopicConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(
                WebSocketTopicConstants.BROKER_TOPIC_PREFIX,
                WebSocketTopicConstants.BROKER_QUEUE_PREFIX
        );
        registry.setApplicationDestinationPrefixes(WebSocketTopicConstants.APP_PREFIX);
        registry.setUserDestinationPrefix(WebSocketTopicConstants.USER_DESTINATION_PREFIX);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(WebSocketTopicConstants.WS_ENDPOINT)
                .setAllowedOriginPatterns("*");

        registry.addEndpoint(WebSocketTopicConstants.WS_ENDPOINT)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
=======
public class WebSocketConfig {
    
>>>>>>> f764710a1bf036f55b692c38351864ccd306c790
}
