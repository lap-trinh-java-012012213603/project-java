package com.analoghub.platform.common.websocket;

public final class WebSocketTopicConstants {

    private WebSocketTopicConstants() {
    }

    public static final String WS_ENDPOINT = "/ws-analoghub";
    public static final String BROKER_TOPIC_PREFIX = "/topic";
    public static final String BROKER_QUEUE_PREFIX = "/queue";
    public static final String APP_PREFIX = "/app";
    public static final String USER_DESTINATION_PREFIX = "/user";

    public static final String TOPIC_ORDERS_TEMPLATE = "/topic/orders/%s";
    public static final String QUEUE_NOTIFICATIONS = "/queue/notifications";
}
