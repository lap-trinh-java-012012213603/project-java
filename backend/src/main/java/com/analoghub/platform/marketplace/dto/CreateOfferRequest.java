package com.analoghub.platform.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOfferRequest(
        @NotBlank(message = "Loại đề nghị thương lượng không được để trống")
        String offerType,

        BigDecimal offeredPriceVnd,

        UUID swapOfferedListingId,

        @Size(max = 1000, message = "Mô tả thiết bị giao lưu tối đa 1000 ký tự")
        String swapItemDescription,

        BigDecimal cashTopupVnd,

        @Size(max = 500, message = "Lời nhắn gửi người bán tối đa 500 ký tự")
        String messageToSeller
) {
}
