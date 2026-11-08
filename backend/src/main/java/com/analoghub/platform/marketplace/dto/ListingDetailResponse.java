package com.analoghub.platform.marketplace.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ListingDetailResponse(
        UUID id,
        String slug,
        String title,
        String description,
        String category,
        String brand,
        String modelName,
        String conditionGrade,
        Map<String, Object> technicalSpecs,
        BigDecimal priceVnd,
        boolean allowPriceOffer,
        boolean allowSwap,
        String preferredSwapItems,
        String provinceCode,
        String districtCode,
        String status,
        List<ListingImageResponse> images,
        SellerProfileSummary sellerProfile,
        long activeOfferCount,
        ViewerOfferSummary viewerActiveOffer,
        Long version,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
    public record SellerProfileSummary(
            UUID userId,
            String fullName,
            String avatarUrl,
            Instant joinedAt,
            double avgSellerRating,
            long totalCompletedSales,
            double itemAsDescribedRate
    ) {
    }

    public record ViewerOfferSummary(
            UUID offerId,
            String offerType,
            BigDecimal offeredPriceVnd,
            BigDecimal cashTopupVnd,
            String status,
            Instant expiresAt
    ) {
    }
}
