package com.analoghub.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecideRoleApplicationRequest(
        @NotNull(message = "Quyết định xét duyệt (decision) không được để trống")
        ApprovalDecision decision,

        @NotBlank(message = "Ghi chú xét duyệt (reviewNote) không được để trống")
        @Size(min = 10, max = 1000, message = "Ghi chú xét duyệt phải từ 10 đến 1000 ký tự")
        String reviewNote
) {
}
