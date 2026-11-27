package com.analoghub.platform.filmlab.facade;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface FilmLabFacade {
    void reserveCapacity(UUID labId, String processType, int rollCount);
    void releaseCapacity(UUID labId, String processType, int rollCount);
    int getEstimatedTurnaroundHours(UUID labId, String processType);

    // DTO trung gian để tránh vi phạm ranh giới Module
    record ServicePackageInfo(
            UUID id, UUID labId, String name, String filmFormat,
            String processType, String scanResolution, BigDecimal unitPriceVnd
    ) {}

    record ProvisionedLabInfo(
            UUID labId,
            UUID ownerUserId,
            String slug,
            String name,
            String status
    ) {}

    record LabReviewModerationInfo(
            UUID reviewId,
            UUID labId,
            UUID authorUserId,
            short rating,
            String comment,
            String moderationStatus
    ) {}

    ServicePackageInfo getServicePackageInfo(UUID packageId);

    ProvisionedLabInfo provisionLabFromApprovedApplication(UUID ownerUserId, Map<String, Object> portfolioPayload);

    ProvisionedLabInfo approveLabOpening(UUID labId);

    List<ProvisionedLabInfo> getLabsByStatus(String status);

    default Optional<ProvisionedLabInfo> findLabById(UUID labId) {
        return Optional.empty();
    }

    default Optional<LabReviewModerationInfo> findReviewById(UUID reviewId) {
        return Optional.empty();
    }

    default Optional<LabReviewModerationInfo> hideReviewByAdmin(UUID reviewId) {
        return Optional.empty();
    }
}