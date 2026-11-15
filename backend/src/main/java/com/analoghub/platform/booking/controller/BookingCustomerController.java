package com.analoghub.platform.booking.controller;

import com.analoghub.platform.booking.dto.CancelOrderRequest;
import com.analoghub.platform.booking.dto.CreateLabOrderRequest;
import com.analoghub.platform.booking.dto.LabOrderResponse;
import com.analoghub.platform.booking.service.LabOrderService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings/orders")
@RequiredArgsConstructor
public class BookingCustomerController {

    private final LabOrderService labOrderService;

    @PostMapping
    @Idempotent
    public ResponseEntity<ApiResponse<LabOrderResponse>> createOrder(
            @Valid @RequestBody CreateLabOrderRequest request
    ) {
        UUID customerId = SecurityContextUtils.getRequiredCurrentUserId();
        LabOrderResponse response = labOrderService.createOrder(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn đặt lịch tráng/scan thành công", response));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<LabOrderResponse>> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(labOrderService.getOrderById(orderId)));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<LabOrderResponse>> cancelOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        UUID customerId = SecurityContextUtils.getRequiredCurrentUserId();
        LabOrderResponse response = labOrderService.cancelOrderByCustomer(customerId, orderId, request.reason());
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công", response));
    }
}
