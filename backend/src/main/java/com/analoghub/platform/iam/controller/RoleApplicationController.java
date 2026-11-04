package com.analoghub.platform.iam.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.iam.dto.RoleApplicationDto;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.SubmitRoleApplicationRequest;
import com.analoghub.platform.iam.service.RoleApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for Role Upgrade Applications (APIs 4.13 - 4.14 of {@code 04-iam.md}).
 */
@RestController
@RequestMapping("/api/v1/role-applications")
@RequiredArgsConstructor
public class RoleApplicationController {

    private final RoleApplicationService roleApplicationService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 24)
    @RateLimit(keyPrefix = "role_app_submit", capacity = 5, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<RoleApplicationDto>> submitRoleApplication(
            @Valid @RequestBody SubmitRoleApplicationRequest request
    ) {
        RoleApplicationDto submitted = roleApplicationService.submitRoleApplication(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Nộp hồ sơ đăng ký nâng cấp vai trò thành công. Ban quản trị sẽ xét duyệt trong vòng 24-48 giờ.",
                        submitted
                ));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "role_app_me", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<List<RoleApplicationDto>>> getMyRoleApplications(
            @RequestParam(value = "status", required = false) RoleApplicationStatus status
    ) {
        List<RoleApplicationDto> applications = roleApplicationService.getMyRoleApplications(status);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách hồ sơ đăng ký vai trò thành công.", applications));
    }
}
