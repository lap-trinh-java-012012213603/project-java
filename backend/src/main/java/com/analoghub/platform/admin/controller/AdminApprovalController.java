package com.analoghub.platform.admin.controller;

import com.analoghub.platform.admin.dto.ApprovalDecision;
import com.analoghub.platform.admin.dto.ApproveLabOpeningRequest;
import com.analoghub.platform.admin.dto.DecideRoleApplicationRequest;
import com.analoghub.platform.admin.dto.RoleApplicationAdminDto;
import com.analoghub.platform.admin.dto.RoleApplicationDecisionResponse;
import com.analoghub.platform.admin.service.AdminRoleAndLabApprovalService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/approvals")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminApprovalController {

    private final AdminRoleAndLabApprovalService adminRoleAndLabApprovalService;

    @GetMapping("/role-applications")
    @RateLimit(keyPrefix = "adm:role-apps:list", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<RoleApplicationAdminDto>>> listRoleApplications(
            @RequestParam(value = "status", required = false, defaultValue = "PENDING") RoleApplicationStatus status,
            @RequestParam(value = "requestedRole", required = false) RoleCode requestedRole,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        PageResponse<RoleApplicationAdminDto> data = adminRoleAndLabApprovalService.listRoleApplications(
                status, requestedRole, page, size
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách hồ sơ đăng ký nâng cấp vai trò thành công.",
                data
        ));
    }

    @PostMapping("/role-applications/{id}/decide")
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 24)
    @RateLimit(keyPrefix = "adm:role-apps:decide", capacity = 30, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<RoleApplicationDecisionResponse>> decideRoleApplication(
            @PathVariable("id") UUID applicationId,
            @Valid @RequestBody DecideRoleApplicationRequest request
    ) {
        RoleApplicationDecisionResponse data = adminRoleAndLabApprovalService.decideRoleApplication(
                applicationId, request
        );
        String message = request.decision() == ApprovalDecision.APPROVE
                ? "Đã phê duyệt đơn đăng ký nâng cấp vai trò và kích hoạt quyền tương ứng."
                : "Đã từ chối đơn đăng ký nâng cấp vai trò.";
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    @GetMapping("/labs")
    @RateLimit(keyPrefix = "adm:labs:list", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<List<FilmLabFacade.ProvisionedLabInfo>>> listLabsByStatus(
            @RequestParam(value = "status", required = false, defaultValue = "PENDING_APPROVAL") String status
    ) {
        List<FilmLabFacade.ProvisionedLabInfo> data = adminRoleAndLabApprovalService.listLabsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách hồ sơ Film Lab theo trạng thái thành công.",
                data
        ));
    }

    @PostMapping("/labs/{labId}/approve")
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 24)
    @RateLimit(keyPrefix = "adm:labs:approve", capacity = 30, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<FilmLabFacade.ProvisionedLabInfo>> approveLabOpening(
            @PathVariable("labId") UUID labId,
            @Valid @RequestBody(required = false) ApproveLabOpeningRequest request
    ) {
        String reviewNote = request != null ? request.reviewNote() : "Approved by Admin";
        FilmLabFacade.ProvisionedLabInfo data = adminRoleAndLabApprovalService.approveLabOpening(labId, reviewNote);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã phê duyệt mở hoạt động Film Lab thành công.",
                data
        ));
    }
}
