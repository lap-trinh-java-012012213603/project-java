package com.analoghub.platform.marketplace.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.marketplace.dto.ConfirmOrderReceivedRequest;
import com.analoghub.platform.marketplace.dto.ConfirmOrderReceivedResponse;
import com.analoghub.platform.marketplace.dto.CreateMarketOrderRequest;
import com.analoghub.platform.marketplace.dto.CreateSellerReviewRequest;
import com.analoghub.platform.marketplace.dto.MarketOrderCreatedResponse;
import com.analoghub.platform.marketplace.dto.MarketOrderDetailResponse;
import com.analoghub.platform.marketplace.dto.MarketOrderSummaryResponse;
import com.analoghub.platform.marketplace.dto.SellerReviewResponse;
import com.analoghub.platform.marketplace.dto.ShipMarketOrderRequest;
import com.analoghub.platform.marketplace.dto.ShipMarketOrderResponse;
import com.analoghub.platform.marketplace.service.MarketplaceOrderService;
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
@RequestMapping("/api/v1/marketplace/orders")
@RequiredArgsConstructor
public class MarketplaceOrderController {

    private final MarketplaceOrderService orderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'EXPERT', 'LAB_OWNER')")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "mkt:create-order", capacity = 10, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<MarketOrderCreatedResponse>> createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateMarketOrderRequest request
    ) {
        UUID buyerUserId = extractUserId(authentication);
        MarketOrderCreatedResponse response = orderService.createOrder(buyerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Tạo đơn hàng C2C thành công. Vui lòng hoàn tất thanh toán Escrow trong vòng 15 phút.",
                        response
                ));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "mkt:my-orders", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<MarketOrderSummaryResponse>>> getMyOrders(
            Authentication authentication,
            @RequestParam String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID currentUserId = extractUserId(authentication);
        PageResponse<MarketOrderSummaryResponse> response = orderService.getMyOrders(
                currentUserId, role, status, page, size
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng C2C thành công.", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "mkt:order-detail", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<MarketOrderDetailResponse>> getOrderDetail(
            Authentication authentication,
            @PathVariable("id") UUID orderId
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdmin = hasAdminAuthority(authentication);
        MarketOrderDetailResponse response = orderService.getOrderDetail(currentUserId, isAdmin, orderId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn hàng C2C thành công.", response));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("isAuthenticated()")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "mkt:ship-order", capacity = 20, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ShipMarketOrderResponse>> shipOrder(
            Authentication authentication,
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody ShipMarketOrderRequest request
    ) {
        UUID sellerUserId = extractUserId(authentication);
        ShipMarketOrderResponse response = orderService.shipOrder(sellerUserId, orderId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Đã xác nhận đóng gói và tạo vận đơn giao hàng C2C thành công.", response)
        );
    }

    @PostMapping("/{id}/confirm-received")
    @PreAuthorize("isAuthenticated()")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "mkt:confirm-received", capacity = 20, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ConfirmOrderReceivedResponse>> confirmReceived(
            Authentication authentication,
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody(required = false) ConfirmOrderReceivedRequest request
    ) {
        UUID buyerUserId = extractUserId(authentication);
        ConfirmOrderReceivedResponse response = orderService.confirmReceived(buyerUserId, orderId, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Xác nhận nhận hàng thành công. Hệ thống đã giải ngân tiền bán hàng vào ví của người bán.",
                response
        ));
    }

    @PostMapping("/{id}/reviews")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "mkt:create-review", capacity = 15, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<SellerReviewResponse>> createSellerReview(
            Authentication authentication,
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody CreateSellerReviewRequest request
    ) {
        UUID buyerUserId = extractUserId(authentication);
        SellerReviewResponse response = orderService.createSellerReview(buyerUserId, orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Cảm ơn bạn đã gửi đánh giá cho người bán.", response));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
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
