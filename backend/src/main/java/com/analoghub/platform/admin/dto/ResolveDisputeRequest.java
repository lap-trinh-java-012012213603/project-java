package com.analoghub.platform.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ResolveDisputeRequest(
        @NotNull(message = "Quyết định phân xử (resolutionDecision) không được để trống")
        DisputeResolutionDecision resolutionDecision,

        @NotNull(message = "Số tiền hoàn trả người mua (refundToBuyerVnd) không được để trống")
        @DecimalMin(value = "0.00", message = "refundToBuyerVnd không được âm")
        BigDecimal refundToBuyerVnd,

        @NotNull(message = "Số tiền giải ngân cho người bán/Lab (releaseToSellerVnd) không được để trống")
        @DecimalMin(value = "0.00", message = "releaseToSellerVnd không được âm")
        BigDecimal releaseToSellerVnd,

        Boolean waivePlatformCommission,

        @NotBlank(message = "Bản án phân xử chi tiết (resolutionNote) không được để trống")
        @Size(min = 20, max = 2000, message = "resolutionNote phải từ 20 đến 2000 ký tự")
        String resolutionNote
) {
}
