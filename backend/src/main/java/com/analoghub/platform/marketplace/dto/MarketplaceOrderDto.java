package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MarketplaceOrderDto(
        UUID id,
        String orderCode,
        UUID buyerUserId,
        UUID sellerUserId,
        String orderType,
        BigDecimal totalPayableVnd,
        String status
) {
}
