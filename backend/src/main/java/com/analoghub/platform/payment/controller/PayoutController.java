package com.analoghub.platform.payment.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.payment.domain.PayoutRequestStatus;
import com.analoghub.platform.payment.dto.CreatePayoutRequest;
import com.analoghub.platform.payment.dto.PayoutResponse;
import com.analoghub.platform.payment.dto.PayoutSummaryDto;
import com.analoghub.platform.payment.service.WalletAndPayoutService;
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

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final WalletAndPayoutService walletAndPayoutService;

    @PostMapping
    @PreAuthorize("hasAnyRole('LAB_OWNER', 'CUSTOMER', 'EXPERT')")
    @Idempotent(required = true, expirationHours = 24)
    @RateLimit(keyPrefix = "pay:payout-create", capacity = 5, windowSeconds = 300, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PayoutResponse>> requestPayout(
            @Valid @RequestBody CreatePayoutRequest request
    ) {
        PayoutResponse data = walletAndPayoutService.requestPayout(request);
        ApiResponse<PayoutResponse> body = ApiResponse.<PayoutResponse>builder()
                .success(true)
                .code("PAYOUT_REQUEST_CREATED")
                .message("Tạo yêu cầu rút tiền thành công. Số tiền đã được tạm khóa để chờ đối soát chuyển khoản.")
                .data(data)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "pay:payout-list", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<PayoutSummaryDto>>> getMyPayouts(
            @RequestParam(required = false) PayoutRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<PayoutSummaryDto> data = walletAndPayoutService.getMyPayouts(status, page, size);
        ApiResponse<PageResponse<PayoutSummaryDto>> body =
                ApiResponse.<PageResponse<PayoutSummaryDto>>builder()
                        .success(true)
                        .code("PAYOUT_LIST_RETRIEVED")
                        .message("Lấy danh sách yêu cầu rút tiền thành công.")
                        .data(data)
                        .timestamp(Instant.now())
                        .build();
        return ResponseEntity.ok(body);
    }
}
