package com.analoghub.platform.admin.controller;

import com.analoghub.platform.admin.dto.AdminWalletLedgerEntryViewDto;
import com.analoghub.platform.admin.dto.DecidePayoutRequest;
import com.analoghub.platform.admin.dto.DecidePayoutResponse;
import com.analoghub.platform.admin.dto.PayoutDecision;
import com.analoghub.platform.admin.dto.PayoutRequestAdminDto;
import com.analoghub.platform.admin.service.AdminPaymentMonitorService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.payment.dto.LedgerReconciliationReportDto;
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

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPaymentMonitorController {

    private final AdminPaymentMonitorService adminPaymentMonitorService;

    @GetMapping("/ledger/reconcile")
    @RateLimit(keyPrefix = "adm:payments:reconcile", capacity = 30, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<LedgerReconciliationReportDto>> reconcileLedger() {
        LedgerReconciliationReportDto report = adminPaymentMonitorService.reconcileWalletLedger();
        return ResponseEntity.ok(ApiResponse.success(
                "Đối soát toàn hệ thống sổ cái kép và ví tiền hoàn tất.",
                report
        ));
    }

    @GetMapping("/ledger")
    @RateLimit(keyPrefix = "adm:payments:ledger", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<AdminWalletLedgerEntryViewDto>>> searchLedgerEntries(
            @RequestParam(value = "walletId", required = false) UUID walletId,
            @RequestParam(value = "ownerUserId", required = false) UUID ownerUserId,
            @RequestParam(value = "journalGroupId", required = false) UUID journalGroupId,
            @RequestParam(value = "entryDirection", required = false) String entryDirection,
            @RequestParam(value = "entryType", required = false) String entryType,
            @RequestParam(value = "fromTime", required = false) Instant fromTime,
            @RequestParam(value = "toTime", required = false) Instant toTime,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        PageResponse<AdminWalletLedgerEntryViewDto> data = adminPaymentMonitorService.searchLedgerEntries(
                walletId,
                ownerUserId,
                journalGroupId,
                entryDirection,
                entryType,
                fromTime,
                toTime,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Tra cứu sổ cái giao dịch ví thành công.",
                data
        ));
    }

    @GetMapping("/payouts")
    @RateLimit(keyPrefix = "adm:payouts:list", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<PayoutRequestAdminDto>>> listPayoutRequests(
            @RequestParam(value = "status", required = false, defaultValue = "PENDING_APPROVAL") String status,
            @RequestParam(value = "requesterUserId", required = false) UUID requesterUserId,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        PageResponse<PayoutRequestAdminDto> data = adminPaymentMonitorService.listPayoutRequests(
                status,
                requesterUserId,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách yêu cầu rút tiền thành công.",
                data
        ));
    }

    @PostMapping("/payouts/{id}/decide")
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 24)
    @RateLimit(keyPrefix = "adm:payouts:decide", capacity = 20, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DecidePayoutResponse>> decidePayoutRequest(
            @PathVariable("id") UUID payoutId,
            @Valid @RequestBody DecidePayoutRequest request
    ) {
        DecidePayoutResponse data = adminPaymentMonitorService.decidePayoutRequest(payoutId, request);
        String message = request.decision() == PayoutDecision.APPROVE_COMPLETED
                ? "Đã xác nhận hoàn tất yêu cầu rút tiền và cập nhật sổ cái ví."
                : "Đã từ chối yêu cầu rút tiền và hoàn trả số dư về ví khả dụng.";
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }
}
