package com.analoghub.platform.admin.controller;

import com.analoghub.platform.admin.dto.DisputeDetailAdminDto;
import com.analoghub.platform.admin.dto.DisputeStatus;
import com.analoghub.platform.admin.dto.DisputeSummaryAdminDto;
import com.analoghub.platform.admin.dto.DisputeType;
import com.analoghub.platform.admin.dto.ResolveDisputeRequest;
import com.analoghub.platform.admin.dto.ResolveDisputeResponse;
import com.analoghub.platform.admin.service.DisputeResolutionService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/disputes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final DisputeResolutionService disputeResolutionService;

    @GetMapping
    @RateLimit(keyPrefix = "adm:disputes:list", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<DisputeSummaryAdminDto>>> listDisputes(
            @RequestParam(value = "status", required = false) DisputeStatus status,
            @RequestParam(value = "disputeType", required = false) DisputeType disputeType,
            @RequestParam(value = "disputeCode", required = false) @Size(max = 32) String disputeCode,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        PageResponse<DisputeSummaryAdminDto> data = disputeResolutionService.listDisputes(
                status, disputeType, disputeCode, page, size
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách hồ sơ tranh chấp thành công.",
                data
        ));
    }

    @GetMapping("/{id}")
    @RateLimit(keyPrefix = "adm:disputes:detail", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DisputeDetailAdminDto>> getDisputeDetail(
            @PathVariable("id") UUID disputeId
    ) {
        DisputeDetailAdminDto data = disputeResolutionService.getDisputeDetail(disputeId);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy chi tiết hồ sơ tranh chấp thành công.",
                data
        ));
    }

    @PostMapping("/{id}/resolve")
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 24)
    @RateLimit(keyPrefix = "adm:disputes:resolve", capacity = 20, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ResolveDisputeResponse>> resolveDispute(
            @PathVariable("id") UUID disputeId,
            @Valid @RequestBody ResolveDisputeRequest request
    ) {
        ResolveDisputeResponse data = disputeResolutionService.resolveDispute(disputeId, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã thực thi phán quyết tranh chấp và tất toán sổ cái kép Escrow thành công.",
                data
        ));
    }
}
