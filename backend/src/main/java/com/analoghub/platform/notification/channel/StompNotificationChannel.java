package com.analoghub.platform.notification.channel;

import com.analoghub.platform.common.websocket.WebSocketTopicConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StompNotificationChannel {

    private final SimpMessagingTemplate messagingTemplate;

    public void pushNewNotification(
            UUID recipientUserId,
            UUID notificationId,
            String notificationType,
            String title,
            String body,
            Map<String, Object> actionPayload,
            long unreadCount,
            Instant createdAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "NEW_NOTIFICATION");
        payload.put("id", notificationId);
        payload.put("notificationType", notificationType);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("actionPayload", actionPayload != null ? actionPayload : Map.of());
        payload.put("unreadCount", unreadCount);
        payload.put("createdAt", createdAt != null ? createdAt : Instant.now());

        messagingTemplate.convertAndSendToUser(
                recipientUserId.toString(),
                WebSocketTopicConstants.QUEUE_NOTIFICATIONS,
                payload
        );
    }

    public void pushUnreadCountSync(UUID recipientUserId, long remainingUnreadCount) {
        Map<String, Object> payload = Map.of(
                "eventType", "UNREAD_COUNT_SYNC",
                "remainingUnreadCount", remainingUnreadCount,
                "syncedAt", Instant.now()
        );
        messagingTemplate.convertAndSendToUser(
                recipientUserId.toString(),
                WebSocketTopicConstants.QUEUE_NOTIFICATIONS,
                payload
        );
    }
}
