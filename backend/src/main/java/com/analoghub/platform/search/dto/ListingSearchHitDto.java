package com.analoghub.platform.search.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record ListingSearchHitDto(
        String listingId,
        String sellerUserId,
        String slug,
        String title,
        String highlightedTitle,
        String category,
        String brand,
        String modelName,
        String conditionGrade,
        BigDecimal priceVnd,
        Boolean allowPriceOffer,
        Boolean allowSwap,
        String preferredSwapItems,
        String thumbnailUrl,
        String provinceCode,
        String districtCode,
        String status,
        Float score,
        Instant createdAt
) {
}

