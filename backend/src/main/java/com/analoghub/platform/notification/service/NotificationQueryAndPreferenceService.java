package com.analoghub.platform.notification.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.notification.channel.StompNotificationChannel;
import com.analoghub.platform.notification.domain.DevicePlatform;
import com.analoghub.platform.notification.domain.DeviceToken;
import com.analoghub.platform.notification.domain.Notification;
import com.analoghub.platform.notification.domain.NotificationPreference;
import com.analoghub.platform.notification.dto.DeactivateDeviceTokenRequest;
import com.analoghub.platform.notification.dto.DeactivateDeviceTokenResponse;
import com.analoghub.platform.notification.dto.DeviceTokenResponse;
import com.analoghub.platform.notification.dto.MarkAllNotificationsReadResponse;
import com.analoghub.platform.notification.dto.MarkNotificationReadResponse;
import com.analoghub.platform.notification.dto.NotificationItemResponse;
import com.analoghub.platform.notification.dto.NotificationPageResponse;
import com.analoghub.platform.notification.dto.NotificationPreferenceResponse;
import com.analoghub.platform.notification.dto.RegisterDeviceTokenRequest;
import com.analoghub.platform.notification.dto.UpdateNotificationPreferencesRequest;
import com.analoghub.platform.notification.repository.DeviceTokenRepository;
import com.analoghub.platform.notification.repository.NotificationPreferenceRepository;
import com.analoghub.platform.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationQueryAndPreferenceService {

    private static final int MAX_ACTIVE_DEVICES_PER_USER = 10;
    private static final Set<String> SUPPORTED_NOTIFICATION_TYPES = Set.of(
            "LAB_ORDER_UPDATE",
            "ROLL_KANBAN_PROGRESS",
            "BLANK_ROLL_REFUND",
            "ARCHIVE_READY_AND_AI_QC",
            "MARKETPLACE_OFFER",
            "MARKETPLACE_ORDER",
            "WALLET_ESCROW",
            "COMMUNITY_ACTIVITY",
            "SYSTEM_ANNOUNCEMENT"
    );
    private static final Set<String> CRITICAL_NON_MUTABLE_CATEGORIES = Set.of(
            "WALLET_ESCROW",
            "BLANK_ROLL_REFUND",
            "LAB_ORDER_UPDATE"
    );

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final StompNotificationChannel stompChannel;

    @Transactional(readOnly = true)
    public NotificationPageResponse getMyNotifications(
            UUID userId,
            boolean unreadOnly,
            String notificationType,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang không hợp lệ");
        }

        String normalizedType = null;
        if (notificationType != null && !notificationType.isBlank()) {
            normalizedType = notificationType.trim().toUpperCase();
            if (!SUPPORTED_NOTIFICATION_TYPES.contains(normalizedType)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh mục thông báo không hợp lệ: " + notificationType);
            }
        }

        PageRequest pageable = PageRequest.of(page, size);
        Page<Notification> notifPage;

        if (unreadOnly && normalizedType != null) {
            notifPage = notificationRepository.findByRecipientUserIdAndIsReadFalseAndNotificationTypeOrderByCreatedAtDesc(
                    userId, normalizedType, pageable
            );
        } else if (unreadOnly) {
            notifPage = notificationRepository.findByRecipientUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable);
        } else if (normalizedType != null) {
            notifPage = notificationRepository.findByRecipientUserIdAndNotificationTypeOrderByCreatedAtDesc(
                    userId, normalizedType, pageable
            );
        } else {
            notifPage = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        long unreadCount = notificationRepository.countByRecipientUserIdAndIsReadFalse(userId);
        List<NotificationItemResponse> items = notifPage.getContent().stream()
                .map(n -> new NotificationItemResponse(
                        n.getId(),
                        n.getNotificationType(),
                        n.getTitle(),
                        n.getBody(),
                        n.getActionPayload(),
                        n.isRead(),
                        n.getCreatedAt()
                ))
                .toList();

        return new NotificationPageResponse(
                unreadCount,
                items,
                page,
                size,
                notifPage.getTotalElements(),
                notifPage.getTotalPages()
        );
    }

    @Transactional
    public MarkNotificationReadResponse markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy thông báo với định danh được cung cấp"
                ));

        if (!notification.getRecipientUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập thông báo này");
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.saveAndFlush(notification);
        }

        long remainingUnread = notificationRepository.countByRecipientUserIdAndIsReadFalse(userId);
        stompChannel.pushUnreadCountSync(userId, remainingUnread);

        return new MarkNotificationReadResponse(notification.getId(), true, remainingUnread);
    }

    @Transactional
    public MarkAllNotificationsReadResponse markAllAsRead(UUID userId) {
        int updatedCount = notificationRepository.markAllAsRead(userId, Instant.now());
        stompChannel.pushUnreadCountSync(userId, 0L);
        return new MarkAllNotificationsReadResponse(updatedCount, 0L);
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences(UUID userId) {
        NotificationPreference pref = preferenceRepository.findByUserId(userId).orElse(null);
        if (pref == null) {
            return new NotificationPreferenceResponse(userId, true, true, true, List.of());
        }
        return new NotificationPreferenceResponse(
                userId,
                pref.isInAppEnabled(),
                pref.isEmailEnabled(),
                pref.isPushEnabled(),
                pref.getMutedCategories() != null ? pref.getMutedCategories() : List.of()
        );
    }

    @Transactional
    public NotificationPreferenceResponse updatePreferences(
            UUID userId,
            UpdateNotificationPreferencesRequest request
    ) {
        Set<String> normalizedMuted = new LinkedHashSet<>();
        for (String cat : request.mutedCategories()) {
            if (cat == null || cat.isBlank()) {
                continue;
            }
            String upper = cat.trim().toUpperCase();
            if (CRITICAL_NON_MUTABLE_CATEGORIES.contains(upper)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Không thể tắt thông báo thuộc nhóm giao dịch tài chính và đơn hàng trọng yếu (" + upper + ")"
                );
            }
            if (!SUPPORTED_NOTIFICATION_TYPES.contains(upper)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh mục thông báo không hợp lệ: " + cat);
            }
            normalizedMuted.add(upper);
        }

        NotificationPreference pref = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> NotificationPreference.builder().userId(userId).build());

        pref.setInAppEnabled(Boolean.TRUE.equals(request.enableInApp()));
        pref.setEmailEnabled(Boolean.TRUE.equals(request.enableEmail()));
        pref.setPushEnabled(Boolean.TRUE.equals(request.enablePush()));
        pref.setMutedCategories(new ArrayList<>(normalizedMuted));

        pref = preferenceRepository.saveAndFlush(pref);

        return new NotificationPreferenceResponse(
                userId,
                pref.isInAppEnabled(),
                pref.isEmailEnabled(),
                pref.isPushEnabled(),
                pref.getMutedCategories()
        );
    }

    @Transactional
    public DeviceTokenResponse registerDeviceToken(UUID userId, RegisterDeviceTokenRequest request) {
        DevicePlatform platform = parsePlatform(request.devicePlatform());
        String tokenStr = request.fcmToken().trim();

        DeviceToken deviceToken = deviceTokenRepository.findByDeviceToken(tokenStr)
                .map(existing -> {
                    existing.setUserId(userId);
                    existing.setPlatform(platform);
                    existing.setActive(true);
                    existing.setUpdatedAt(Instant.now());
                    return existing;
                })
                .orElseGet(() -> DeviceToken.builder()
                        .userId(userId)
                        .deviceToken(tokenStr)
                        .platform(platform)
                        .isActive(true)
                        .build());

        deviceToken = deviceTokenRepository.saveAndFlush(deviceToken);

        List<DeviceToken> activeDevices = deviceTokenRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtAsc(userId);
        if (activeDevices.size() > MAX_ACTIVE_DEVICES_PER_USER) {
            int excess = activeDevices.size() - MAX_ACTIVE_DEVICES_PER_USER;
            for (int i = 0; i < excess; i++) {
                DeviceToken oldest = activeDevices.get(i);
                oldest.setActive(false);
                deviceTokenRepository.save(oldest);
            }
        }

        return new DeviceTokenResponse(
                deviceToken.getId(),
                deviceToken.getUserId(),
                deviceToken.getPlatform().name(),
                deviceToken.isActive(),
                deviceToken.getUpdatedAt()
        );
    }

    @Transactional
    public DeactivateDeviceTokenResponse deactivateDeviceToken(
            UUID userId,
            DeactivateDeviceTokenRequest request
    ) {
        String tokenStr = request.fcmToken().trim();
        Optional<DeviceToken> existingOpt = deviceTokenRepository.findByDeviceTokenAndUserId(tokenStr, userId);

        boolean deactivated = false;
        if (existingOpt.isPresent()) {
            DeviceToken dt = existingOpt.get();
            if (dt.isActive()) {
                dt.setActive(false);
                deviceTokenRepository.saveAndFlush(dt);
                deactivated = true;
            }
        }
        return new DeactivateDeviceTokenResponse(deactivated);
    }

    private DevicePlatform parsePlatform(String raw) {
        try {
            return DevicePlatform.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "devicePlatform chỉ hỗ trợ WEB, ANDROID hoặc IOS");
        }
    }
}
