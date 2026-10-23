package com.analoghub.platform.common.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StompNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(StompNotificationPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public StompNotificationPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishOrderStatus(String orderCode, Object payload) {
        String destination = String.format(WebSocketTopicConstants.TOPIC_ORDERS_TEMPLATE, orderCode);
        log.info("Sending STOMP message to order topic [{}]: {}", destination, payload);
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void publishRollStatus(String orderCode, String rollTrackingCode, String status, String note) {
        Map<String, Object> payload = Map.of(
                "orderCode", orderCode,
                "rollTrackingCode", rollTrackingCode,
                "status", status,
                "note", note != null ? note : "",
                "timestamp", System.currentTimeMillis()
        );
        publishOrderStatus(orderCode, payload);
    }

    public void publishUserNotification(String userId, Object payload) {
        log.info("Sending STOMP notification to user [{}] queue [{}]: {}",
                userId, WebSocketTopicConstants.QUEUE_NOTIFICATIONS, payload);
        messagingTemplate.convertAndSendToUser(
                userId,
                WebSocketTopicConstants.QUEUE_NOTIFICATIONS,
                payload
        );
    }
}
