package com.analoghub.platform.filmlab.repository;

import com.analoghub.platform.filmlab.domain.LabReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LabReviewRepository extends JpaRepository<LabReview, UUID> {
    boolean existsByOrderId(UUID orderId);
}