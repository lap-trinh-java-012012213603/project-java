package com.analoghub.platform.payment.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.payment.domain.LedgerEntryDirection;
import com.analoghub.platform.payment.domain.LedgerEntryType;
import com.analoghub.platform.payment.dto.WalletLedgerEntryDto;
import com.analoghub.platform.payment.dto.WalletSummaryResponse;
import com.analoghub.platform.payment.service.WalletAndPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletAndPayoutService walletAndPayoutService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "pay:wallet-me", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<WalletSummaryResponse>> getMyWallet() {
        WalletSummaryResponse data = walletAndPayoutService.getMyWallet();
        ApiResponse<WalletSummaryResponse> body = ApiResponse.<WalletSummaryResponse>builder()
                .success(true)
                .code("WALLET_RETRIEVED")
                .message("Lấy thông tin ví điện tử thành công.")
                .data(data)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/me/ledger")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "pay:wallet-ledger", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<WalletLedgerEntryDto>>> getMyWalletLedger(
            @RequestParam(required = false) LedgerEntryDirection entryDirection,
            @RequestParam(required = false) LedgerEntryType entryType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<WalletLedgerEntryDto> data = walletAndPayoutService.getMyWalletLedger(
                entryDirection,
                entryType,
                fromDate,
                toDate,
                page,
                size
        );
        ApiResponse<PageResponse<WalletLedgerEntryDto>> body =
                ApiResponse.<PageResponse<WalletLedgerEntryDto>>builder()
                        .success(true)
                        .code("WALLET_LEDGER_RETRIEVED")
                        .message("Lấy lịch sử bút toán sổ cái thành công.")
                        .data(data)
                        .timestamp(Instant.now())
                        .build();
        return ResponseEntity.ok(body);
    }
}
