package com.analoghub.platform.notification.facade;

import com.analoghub.platform.notification.dto.SendNotificationRequest;
import com.analoghub.platform.notification.service.NotificationDispatcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class NotificationFacadeImpl implements NotificationFacade {

    private final NotificationDispatcherService dispatcherService;

    @Override
    public void sendNotification(SendNotificationRequest request) {
        dispatcherService.dispatch(request);
    }

    @Override
    public void sendSystemAlert(UUID recipientUserId, String title, String body) {
        dispatcherService.sendSystemAlert(recipientUserId, title, body);
    }
}
