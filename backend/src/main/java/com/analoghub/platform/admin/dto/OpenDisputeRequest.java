package com.analoghub.platform.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OpenDisputeRequest(
        @NotNull(message = "Loại tranh chấp (disputeType) không được để trống")
        DisputeType disputeType,

        @NotNull(message = "Mã đơn hàng (referenceOrderId) không được để trống")
        UUID referenceOrderId,

        @NotNull(message = "Nhóm nguyên nhân khiếu nại (reasonCategory) không được để trống")
        DisputeReasonCategory reasonCategory,

        @NotBlank(message = "Nội dung khiếu nại (claimDescription) không được để trống")
        @Size(min = 20, max = 3000, message = "Nội dung khiếu nại phải từ 20 đến 3000 ký tự")
        String claimDescription,

        @NotNull(message = "Số tiền đề nghị hoàn trả (requestedRefundAmountVnd) không được để trống")
        @DecimalMin(value = "10000.00", message = "Số tiền đề nghị hoàn trả tối thiểu là 10.000 VND")
        BigDecimal requestedRefundAmountVnd,

        @Size(max = 10, message = "Tối đa 10 tệp bằng chứng khởi tạo")
        List<String> initialEvidenceS3Keys,

        UUID attachedAiScanReportId
) {
}
