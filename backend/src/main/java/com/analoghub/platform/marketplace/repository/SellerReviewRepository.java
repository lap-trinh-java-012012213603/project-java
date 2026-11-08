package com.analoghub.platform.marketplace.repository;

import com.analoghub.platform.marketplace.domain.SellerReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SellerReviewRepository extends JpaRepository<SellerReview, UUID> {

    boolean existsByMarketOrderId(UUID marketOrderId);

    List<SellerReview> findBySellerUserId(UUID sellerUserId);
}
