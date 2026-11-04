package com.analoghub.platform.delivery.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.delivery.dto.DeliveryTaskDetailResponse;
import com.analoghub.platform.delivery.service.DeliveryPartnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery/tasks")
@RequiredArgsConstructor
public class DeliveryTaskController {

    private final DeliveryPartnerService deliveryPartnerService;

    @GetMapping("/{taskId}")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "dlv:detail", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DeliveryTaskDetailResponse>> getTaskDetail(@PathVariable UUID taskId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        UUID currentUserId = extractUserId(auth);
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ADMIN".equals(a));

        DeliveryTaskDetailResponse result = deliveryPartnerService.getTaskDetail(taskId, currentUserId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Delivery task detail retrieved", result));
    }

    private UUID extractUserId(Authentication auth) {
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
