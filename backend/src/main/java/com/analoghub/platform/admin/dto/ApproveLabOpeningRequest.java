package com.analoghub.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApproveLabOpeningRequest(
        @NotBlank(message = "Ghi chú phê duyệt (reviewNote) không được để trống")
        @Size(min = 5, max = 1000, message = "Ghi chú phê duyệt phải từ 5 đến 1000 ký tự")
        String reviewNote
) {
}
