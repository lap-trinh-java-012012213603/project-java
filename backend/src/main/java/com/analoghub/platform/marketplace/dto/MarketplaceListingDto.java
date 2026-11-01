package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MarketplaceListingDto(
        UUID id,
        UUID sellerUserId,
        String slug,
        String title,
        String description,
        String category,
        String brand,
        String modelName,
        String conditionGrade,
        BigDecimal priceVnd,
        boolean allowPriceOffer,
        boolean allowSwap,
        String provinceCode,
        String districtCode,
        String status
) {
}
