package com.analoghub.platform.notification.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.notification.dto.DeactivateDeviceTokenRequest;
import com.analoghub.platform.notification.dto.DeactivateDeviceTokenResponse;
import com.analoghub.platform.notification.dto.DeviceTokenResponse;
import com.analoghub.platform.notification.dto.MarkAllNotificationsReadResponse;
import com.analoghub.platform.notification.dto.MarkNotificationReadResponse;
import com.analoghub.platform.notification.dto.NotificationPageResponse;
import com.analoghub.platform.notification.dto.NotificationPreferenceResponse;
import com.analoghub.platform.notification.dto.RegisterDeviceTokenRequest;
import com.analoghub.platform.notification.dto.UpdateNotificationPreferencesRequest;
import com.analoghub.platform.notification.service.NotificationQueryAndPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryAndPreferenceService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ntf:list-notifications", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getMyNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String notificationType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = extractRequiredUserId(authentication);
        NotificationPageResponse response = notificationService.getMyNotifications(
                userId, unreadOnly, notificationType, page, size
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thông báo thành công.", response));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ntf:mark-read", capacity = 120, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<MarkNotificationReadResponse>> markAsRead(
            Authentication authentication,
            @PathVariable("id") UUID notificationId
    ) {
        UUID userId = extractRequiredUserId(authentication);
        MarkNotificationReadResponse response = notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu thông báo là đã đọc.", response));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ntf:mark-all-read", capacity = 20, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<MarkAllNotificationsReadResponse>> markAllAsRead(
            Authentication authentication
    ) {
        UUID userId = extractRequiredUserId(authentication);
        MarkAllNotificationsReadResponse response = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu tất cả thông báo là đã đọc.", response));
    }

    @GetMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ntf:get-prefs", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getPreferences(
            Authentication authentication
    ) {
        UUID userId = extractRequiredUserId(authentication);
        NotificationPreferenceResponse response = notificationService.getPreferences(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy cấu hình tùy chọn thông báo thành công.", response));
    }

    @PutMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ntf:update-prefs", capacity = 30, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreferences(
            Authentication authentication,
            @Valid @RequestBody UpdateNotificationPreferencesRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        NotificationPreferenceResponse response = notificationService.updatePreferences(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật tùy chọn nhận thông báo thành công.", response));
    }

    @PostMapping("/device-tokens")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ntf:register-token", capacity = 30, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DeviceTokenResponse>> registerDeviceToken(
            Authentication authentication,
            @Valid @RequestBody RegisterDeviceTokenRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        DeviceTokenResponse response = notificationService.registerDeviceToken(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Đăng ký thiết bị nhận thông báo đẩy thành công.", response));
    }

    @DeleteMapping("/device-tokens")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ntf:delete-token", capacity = 30, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DeactivateDeviceTokenResponse>> deactivateDeviceToken(
            Authentication authentication,
            @Valid @RequestBody DeactivateDeviceTokenRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        DeactivateDeviceTokenResponse response = notificationService.deactivateDeviceToken(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Đã hủy đăng ký nhận thông báo đẩy cho thiết bị hiện tại.", response));
    }

    private UUID extractRequiredUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Chưa xác thực người dùng");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Định danh người dùng không hợp lệ");
        }
    }
}
