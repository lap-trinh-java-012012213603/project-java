package com.analoghub.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecidePayoutRequest(
        @NotNull(message = "decision không được để trống")
        PayoutDecision decision,

        @Size(min = 5, max = 100, message = "bankTransferRef phải từ 5 đến 100 ký tự")
        String bankTransferRef,

        @NotBlank(message = "reviewNote không được để trống")
        @Size(min = 5, max = 500, message = "reviewNote phải từ 5 đến 500 ký tự")
        String reviewNote
) {
}
