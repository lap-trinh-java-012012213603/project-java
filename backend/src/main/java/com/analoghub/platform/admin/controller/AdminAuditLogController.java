package com.analoghub.platform.admin.controller;

import com.analoghub.platform.admin.dto.AdminAuditLogDto;
import com.analoghub.platform.admin.service.AdminAuditLogService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    @GetMapping
    @RateLimit(keyPrefix = "adm:audit-logs:search", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<AdminAuditLogDto>>> searchAuditLogs(
            @RequestParam(value = "actorUserId", required = false) UUID actorUserId,
            @RequestParam(value = "actionCode", required = false) @Size(max = 64) String actionCode,
            @RequestParam(value = "targetEntityType", required = false) @Size(max = 64) String targetEntityType,
            @RequestParam(value = "targetEntityId", required = false) UUID targetEntityId,
            @RequestParam(value = "fromTime", required = false) Instant fromTime,
            @RequestParam(value = "toTime", required = false) Instant toTime,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        PageResponse<AdminAuditLogDto> data = adminAuditLogService.searchAuditLogs(
                actorUserId,
                actionCode,
                targetEntityType,
                targetEntityId,
                fromTime,
                toTime,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Tra cứu nhật ký kiểm toán bất biến thành công.",
                data
        ));
    }
}
