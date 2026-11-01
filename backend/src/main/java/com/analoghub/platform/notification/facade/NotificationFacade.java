package com.analoghub.platform.notification.facade;

import com.analoghub.platform.notification.dto.SendNotificationRequest;
import java.util.UUID;

public interface NotificationFacade {

    void sendNotification(SendNotificationRequest request);

    void sendSystemAlert(UUID recipientUserId, String title, String body);
}
