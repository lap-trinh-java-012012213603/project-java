package com.analoghub.platform.payment.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.payment.dto.CheckoutResponse;
import com.analoghub.platform.payment.dto.CreateCheckoutRequest;
import com.analoghub.platform.payment.dto.TransactionStatusResponse;
import com.analoghub.platform.payment.service.PaymentCheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentCheckoutService paymentCheckoutService;

    @PostMapping("/checkout-url")
    @PreAuthorize("isAuthenticated()")
    @Idempotent(required = true, expirationHours = 24)
    @RateLimit(keyPrefix = "pay:checkout", capacity = 10, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<CheckoutResponse>> createCheckout(
            @Valid @RequestBody CreateCheckoutRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = extractClientIp(servletRequest);
        CheckoutResponse data = paymentCheckoutService.createCheckout(request, clientIp);
        ApiResponse<CheckoutResponse> body = ApiResponse.<CheckoutResponse>builder()
                .success(true)
                .code("PAYMENT_CHECKOUT_CREATED")
                .message("Khởi tạo giao dịch thanh toán thành công.")
                .data(data)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/transactions/{txnCode}")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "pay:txn-status", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<TransactionStatusResponse>> getTransactionStatus(
            @PathVariable String txnCode
    ) {
        TransactionStatusResponse data = paymentCheckoutService.getTransactionStatus(txnCode);
        ApiResponse<TransactionStatusResponse> body = ApiResponse.<TransactionStatusResponse>builder()
                .success(true)
                .code("PAYMENT_TRANSACTION_RETRIEVED")
                .message("Lấy thông tin trạng thái giao dịch thành công.")
                .data(data)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.ok(body);
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
    }
}
