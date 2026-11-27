package com.analoghub.platform.admin.controller;

import com.analoghub.platform.admin.dto.ModerationTicketAdminDto;
import com.analoghub.platform.admin.dto.ModerationTicketResolutionResponse;
import com.analoghub.platform.admin.dto.ResolveModerationTicketRequest;
import com.analoghub.platform.admin.service.ContentModerationService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/moderation/tickets")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminModerationController {

    private final ContentModerationService contentModerationService;

    @GetMapping
    @RateLimit(keyPrefix = "adm:mod:list", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<ModerationTicketAdminDto>>> listModerationTickets(
            @RequestParam(value = "status", required = false, defaultValue = "OPEN") String status,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "minReportCount", defaultValue = "1") @Min(1) int minReportCount,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        PageResponse<ModerationTicketAdminDto> data = contentModerationService.listModerationTickets(
                status,
                targetType,
                minReportCount,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách phiếu kiểm duyệt nội dung thành công.",
                data
        ));
    }

    @PatchMapping("/{id}")
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 24)
    @RateLimit(keyPrefix = "adm:mod:resolve", capacity = 30, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ModerationTicketResolutionResponse>> resolveModerationTicket(
            @PathVariable("id") UUID ticketId,
            @Valid @RequestBody ResolveModerationTicketRequest request
    ) {
        ModerationTicketResolutionResponse data = contentModerationService.resolveModerationTicket(
                ticketId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Đã thực thi quyết định kiểm duyệt nội dung và cập nhật trạng thái hệ thống.",
                data
        ));
    }
}
