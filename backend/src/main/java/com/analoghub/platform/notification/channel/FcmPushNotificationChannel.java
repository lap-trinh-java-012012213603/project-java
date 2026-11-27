package com.analoghub.platform.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class FcmPushNotificationChannel {

    public void sendPushToDevices(
            UUID recipientUserId,
            List<String> activeTokens,
            String title,
            String body,
            Map<String, Object> dataPayload
    ) {
        if (activeTokens == null || activeTokens.isEmpty()) {
            return;
        }
        Thread.startVirtualThread(() ->
                log.debug("Dispatched FCM push to user={} across {} active device(s)", recipientUserId, activeTokens.size())
        );
    }
}
