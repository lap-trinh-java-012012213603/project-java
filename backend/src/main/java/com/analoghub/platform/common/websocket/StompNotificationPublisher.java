package com.analoghub.platform.common.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompNotificationPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishToTopic(String destination, Object payload) {
        log.debug("Sending STOMP message to topic [{}]: {}", destination, payload);
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void publishOrderStatus(String orderCode, Object payload) {
        String destination = String.format(WebSocketTopicConstants.TOPIC_ORDERS_TEMPLATE, orderCode);
        log.info("Sending STOMP message to order topic [{}]: {}", destination, payload);
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void publishOrderRollsProgress(UUID orderId, Object payload) {
        String destination = WebSocketTopicConstants.orderRollsTopic(orderId);
        log.info("Sending STOMP roll progress to [{}]: {}", destination, payload);
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void publishLabKanbanUpdate(UUID labId, Object payload) {
        String destination = WebSocketTopicConstants.labKanbanTopic(labId);
        log.info("Sending STOMP lab kanban update to [{}]: {}", destination, payload);
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void publishDeliveryLocation(UUID taskId, Object payload) {
        String destination = WebSocketTopicConstants.deliveryLocationTopic(taskId);
        log.debug("Sending STOMP delivery location to [{}]: {}", destination, payload);
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

    public void publishUserNotification(UUID userId, Object payload) {
        publishUserNotification(userId.toString(), payload);
    }

    public void publishUserMessage(String userId, Object payload) {
        log.info("Sending STOMP chat message to user [{}] queue [{}]: {}",
                userId, WebSocketTopicConstants.QUEUE_MESSAGES, payload);
        messagingTemplate.convertAndSendToUser(
                userId,
                WebSocketTopicConstants.QUEUE_MESSAGES,
                payload
        );
    }

    public void publishUserMessage(UUID userId, Object payload) {
        publishUserMessage(userId.toString(), payload);
    }
}
