package com.analoghub.platform.common.websocket;

import java.util.UUID;

public final class WebSocketTopicConstants {

    private WebSocketTopicConstants() {
    }

    public static final String WS_ENDPOINT = "/ws";
    public static final String BROKER_TOPIC_PREFIX = "/topic";
    public static final String BROKER_QUEUE_PREFIX = "/queue";
    public static final String APP_PREFIX = "/app";
    public static final String USER_DESTINATION_PREFIX = "/user";

    // Client -> Server (@MessageMapping)
    public static final String APP_CHAT_SEND = "/chat.send";
    public static final String APP_CHAT_SEND_FULL = "/app/chat.send";

    // Server -> User Queues (convertAndSendToUser)
    public static final String QUEUE_MESSAGES = "/queue/messages";
    public static final String QUEUE_NOTIFICATIONS = "/queue/notifications";
    public static final String USER_QUEUE_MESSAGES = "/user/queue/messages";
    public static final String USER_QUEUE_NOTIFICATIONS = "/user/queue/notifications";

    // Server -> Broadcast Topics (convertAndSend)
    public static final String TOPIC_ORDERS_TEMPLATE = "/topic/orders/%s";
    public static final String TOPIC_ORDER_ROLLS_TEMPLATE = "/topic/orders/%s/rolls";
    public static final String TOPIC_LAB_KANBAN_TEMPLATE = "/topic/labs/%s/kanban";
    public static final String TOPIC_DELIVERY_LOCATION_TEMPLATE = "/topic/delivery/%s/location";

    public static String orderRollsTopic(UUID orderId) {
        return String.format(TOPIC_ORDER_ROLLS_TEMPLATE, orderId);
    }

    public static String orderRollsTopic(String orderId) {
        return String.format(TOPIC_ORDER_ROLLS_TEMPLATE, orderId);
    }

    public static String labKanbanTopic(UUID labId) {
        return String.format(TOPIC_LAB_KANBAN_TEMPLATE, labId);
    }

    public static String labKanbanTopic(String labId) {
        return String.format(TOPIC_LAB_KANBAN_TEMPLATE, labId);
    }

    public static String deliveryLocationTopic(UUID taskId) {
        return String.format(TOPIC_DELIVERY_LOCATION_TEMPLATE, taskId);
    }

    public static String deliveryLocationTopic(String taskId) {
        return String.format(TOPIC_DELIVERY_LOCATION_TEMPLATE, taskId);
    }
}
