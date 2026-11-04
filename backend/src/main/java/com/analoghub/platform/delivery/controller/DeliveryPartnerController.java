package com.analoghub.platform.delivery.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.delivery.dto.AcceptDeliveryTaskRequest;
import com.analoghub.platform.delivery.dto.AvailableDeliveryTaskResponse;
import com.analoghub.platform.delivery.dto.DeliveryTaskAcceptedResponse;
import com.analoghub.platform.delivery.dto.DeliveryTaskStatusUpdatedResponse;
import com.analoghub.platform.delivery.dto.DriverLocationUpdateRequest;
import com.analoghub.platform.delivery.dto.DriverLocationUpdatedResponse;
import com.analoghub.platform.delivery.dto.UpdateDeliveryTaskStatusRequest;
import com.analoghub.platform.delivery.service.DeliveryPartnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery/tasks")
@PreAuthorize("hasRole('DELIVERY_PARTNER')")
@RequiredArgsConstructor
public class DeliveryPartnerController {

    private final DeliveryPartnerService deliveryPartnerService;

    @GetMapping("/available")
    @RateLimit(keyPrefix = "dlv:available", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<AvailableDeliveryTaskResponse>>> getAvailableTasks(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "10.0") double radiusKm,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<AvailableDeliveryTaskResponse> result = deliveryPartnerService.getAvailableTasks(
                lat, lon, radiusKm, direction, provinceCode, page, size
        );
        return ResponseEntity.ok(ApiResponse.success("Available delivery tasks retrieved", result));
    }

    @PostMapping("/{taskId}/accept")
    @Idempotent(required = true, expirationHours = 24)
    @RateLimit(keyPrefix = "dlv:accept", capacity = 15, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DeliveryTaskAcceptedResponse>> acceptTask(
            @PathVariable UUID taskId,
            @Valid @RequestBody AcceptDeliveryTaskRequest request
    ) {
        UUID driverUserId = currentUserId();
        DeliveryTaskAcceptedResponse result = deliveryPartnerService.acceptTask(taskId, driverUserId, request);
        return ResponseEntity.ok(ApiResponse.success("Delivery task accepted", result));
    }

    @PatchMapping("/{taskId}/status")
    @Idempotent(required = true, expirationHours = 24)
    @RateLimit(keyPrefix = "dlv:status", capacity = 30, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DeliveryTaskStatusUpdatedResponse>> updateTaskStatus(
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateDeliveryTaskStatusRequest request
    ) {
        UUID driverUserId = currentUserId();
        DeliveryTaskStatusUpdatedResponse result = deliveryPartnerService.updateTaskStatus(taskId, driverUserId, request);
        return ResponseEntity.ok(ApiResponse.success("Delivery task status updated", result));
    }

    @PostMapping("/{taskId}/location")
    @RateLimit(keyPrefix = "dlv:gps", capacity = 30, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DriverLocationUpdatedResponse>> reportLocation(
            @PathVariable UUID taskId,
            @Valid @RequestBody DriverLocationUpdateRequest request
    ) {
        UUID driverUserId = currentUserId();
        DriverLocationUpdatedResponse result = deliveryPartnerService.reportLocation(taskId, driverUserId, request);
        return ResponseEntity.ok(ApiResponse.success("Driver location updated", result));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (auth.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
