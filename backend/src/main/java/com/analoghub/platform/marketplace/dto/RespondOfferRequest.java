package com.analoghub.platform.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RespondOfferRequest(
        @NotBlank(message = "Hành động phản hồi không được để trống")
        String action,

        BigDecimal counterAmountVnd,

        @Size(max = 500, message = "Ghi chú phản hồi tối đa 500 ký tự")
        String responseNote
) {
}
