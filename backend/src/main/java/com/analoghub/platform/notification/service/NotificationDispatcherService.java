package com.analoghub.platform.notification.service;

import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.notification.channel.EmailNotificationChannel;
import com.analoghub.platform.notification.channel.FcmPushNotificationChannel;
import com.analoghub.platform.notification.channel.StompNotificationChannel;
import com.analoghub.platform.notification.domain.DeviceToken;
import com.analoghub.platform.notification.domain.Notification;
import com.analoghub.platform.notification.domain.NotificationPreference;
import com.analoghub.platform.notification.dto.SendNotificationRequest;
import com.analoghub.platform.notification.repository.DeviceTokenRepository;
import com.analoghub.platform.notification.repository.NotificationPreferenceRepository;
import com.analoghub.platform.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationDispatcherService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final StompNotificationChannel stompChannel;
    private final EmailNotificationChannel emailChannel;
    private final FcmPushNotificationChannel fcmPushChannel;
    private final IamFacade iamFacade;

    @Transactional
    public void dispatch(SendNotificationRequest request) {
        if (request == null || request.recipientUserId() == null) {
            return;
        }

        UUID recipientId = request.recipientUserId();
        UUID sourceEventId = request.sourceEventId();

        if (sourceEventId != null
                && notificationRepository.existsByRecipientUserIdAndSourceEventId(recipientId, sourceEventId)) {
            return;
        }

        NotificationPreference pref = preferenceRepository.findByUserId(recipientId)
                .orElseGet(() -> NotificationPreference.builder()
                        .userId(recipientId)
                        .inAppEnabled(true)
                        .emailEnabled(true)
                        .pushEnabled(true)
                        .mutedCategories(List.of())
                        .build());

        String type = request.notificationType() != null && !request.notificationType().isBlank()
                ? request.notificationType().trim().toUpperCase()
                : "SYSTEM_ANNOUNCEMENT";

        if (pref.getMutedCategories() != null && pref.getMutedCategories().contains(type)) {
            return;
        }

        Map<String, Object> actionPayload = request.actionPayload() != null
                ? new HashMap<>(request.actionPayload())
                : new HashMap<>();

        UUID savedNotificationId = null;
        Instant createdAt = Instant.now();

        if (pref.isInAppEnabled()) {
            Notification notification = Notification.builder()
                    .recipientUserId(recipientId)
                    .sourceEventId(sourceEventId)
                    .notificationType(type)
                    .title(request.title())
                    .body(request.body())
                    .actionPayload(actionPayload)
                    .isRead(false)
                    .createdAt(createdAt)
                    .build();
            notification = notificationRepository.saveAndFlush(notification);
            savedNotificationId = notification.getId();
            createdAt = notification.getCreatedAt();
        }

        long unreadCount = notificationRepository.countByRecipientUserIdAndIsReadFalse(recipientId);
        List<String> activeTokens = pref.isPushEnabled()
                ? deviceTokenRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtAsc(recipientId)
                        .stream()
                        .map(DeviceToken::getDeviceToken)
                        .toList()
                : List.of();

        String recipientEmail = null;
        if (pref.isEmailEnabled()) {
            recipientEmail = iamFacade.findUserById(recipientId)
                    .map(UserSummaryDto::email)
                    .orElse(null);
        }

        final UUID finalId = savedNotificationId != null ? savedNotificationId : UUID.randomUUID();
        final Instant finalCreatedAt = createdAt;
        final String finalEmail = recipientEmail;
        final boolean inAppEnabled = pref.isInAppEnabled();
        final boolean pushEnabled = pref.isPushEnabled();
        final boolean emailEnabled = pref.isEmailEnabled();

        Runnable dispatchTask = () -> {
            if (inAppEnabled) {
                stompChannel.pushNewNotification(
                        recipientId,
                        finalId,
                        type,
                        request.title(),
                        request.body(),
                        actionPayload,
                        unreadCount,
                        finalCreatedAt
                );
            }
            if (pushEnabled && !activeTokens.isEmpty()) {
                fcmPushChannel.sendPushToDevices(
                        recipientId,
                        activeTokens,
                        request.title(),
                        request.body(),
                        actionPayload
                );
            }
            if (emailEnabled && finalEmail != null) {
                emailChannel.sendEmailAsync(finalEmail, request.title(), request.body());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchTask.run();
                }
            });
        } else {
            dispatchTask.run();
        }
    }

    @Transactional
    public void sendSystemAlert(UUID recipientUserId, String title, String body) {
        dispatch(new SendNotificationRequest(
                recipientUserId,
                "SYSTEM_ANNOUNCEMENT",
                title,
                body,
                Map.of("targetScreen", "NOTIFICATIONS"),
                UUID.randomUUID()
        ));
    }
}
