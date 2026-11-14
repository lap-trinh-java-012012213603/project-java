package com.analoghub.platform.booking.controller;

import com.analoghub.platform.booking.dto.CancelOrderRequest;
import com.analoghub.platform.booking.dto.LabOrderResponse;
import com.analoghub.platform.booking.service.LabOrderService;
import com.analoghub.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my-labs/{labId}/orders")
@RequiredArgsConstructor
public class BookingLabOwnerController {

    private final LabOrderService labOrderService;

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<ApiResponse<LabOrderResponse>> rejectOrder(
            @PathVariable UUID labId,
            @PathVariable UUID orderId,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        LabOrderResponse response = labOrderService.rejectOrderByLab(labId, orderId, request.reason());
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối đơn hàng và hoàn trả công suất", response));
    }
}