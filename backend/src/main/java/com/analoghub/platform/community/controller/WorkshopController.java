package com.analoghub.platform.community.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.community.dto.CreateWorkshopRequest;
import com.analoghub.platform.community.dto.WorkshopCheckInRequest;
import com.analoghub.platform.community.dto.WorkshopCheckInResponse;
import com.analoghub.platform.community.dto.WorkshopRegistrationResponse;
import com.analoghub.platform.community.dto.WorkshopResponse;
import com.analoghub.platform.community.service.WorkshopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community/workshops")
@RequiredArgsConstructor
public class WorkshopController {

    private final WorkshopService workshopService;

    @PostMapping
    @PreAuthorize("hasAnyRole('EXPERT', 'LAB_OWNER')")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "com:create-workshop", capacity = 5, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<WorkshopResponse>> createWorkshop(
            Authentication authentication,
            @Valid @RequestBody CreateWorkshopRequest request
    ) {
        UUID organizerId = extractRequiredUserId(authentication);
        WorkshopResponse response = workshopService.createWorkshop(organizerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Khởi tạo sự kiện Workshop/Photowalk thành công.", response));
    }

    @GetMapping
    @RateLimit(keyPrefix = "com:list-workshops", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<PageResponse<WorkshopResponse>>> listWorkshops(
            @RequestParam(required = false) String workshopType,
            @RequestParam(defaultValue = "UPCOMING") String status,
            @RequestParam(defaultValue = "false") boolean freeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        PageResponse<WorkshopResponse> response = workshopService.listWorkshops(
                workshopType, status, freeOnly, page, size
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sự kiện Workshop/Photowalk thành công.", response));
    }

    @PostMapping("/{id}/register")
    @PreAuthorize("isAuthenticated()")
    @Idempotent(expirationHours = 24, required = false)
    @RateLimit(keyPrefix = "com:register-workshop", capacity = 10, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<WorkshopRegistrationResponse>> registerWorkshop(
            Authentication authentication,
            @PathVariable("id") UUID workshopId
    ) {
        UUID userId = extractRequiredUserId(authentication);
        WorkshopRegistrationResponse response = workshopService.registerWorkshop(userId, workshopId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký giữ chỗ sự kiện thành công.", response));
    }

    @PostMapping("/{id}/checkin")
    @PreAuthorize("hasAnyRole('EXPERT', 'LAB_OWNER', 'ADMIN')")
    @RateLimit(keyPrefix = "com:checkin-workshop", capacity = 120, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<WorkshopCheckInResponse>> checkInParticipant(
            Authentication authentication,
            @PathVariable("id") UUID workshopId,
            @Valid @RequestBody WorkshopCheckInRequest request
    ) {
        UUID actorUserId = extractRequiredUserId(authentication);
        boolean isAdmin = hasAdminAuthority(authentication);
        WorkshopCheckInResponse response = workshopService.checkInParticipant(
                actorUserId, isAdmin, workshopId, request
        );
        return ResponseEntity.ok(ApiResponse.success("Check-in người tham gia thành công.", response));
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

    private boolean hasAdminAuthority(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ADMIN".equals(role)) {
                return true;
            }
        }
        return false;
    }
}
