package com.analoghub.platform.marketplace.repository;

import com.analoghub.platform.marketplace.domain.ListingStatus;
import com.analoghub.platform.marketplace.domain.MarketplaceListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, UUID> {

    Optional<MarketplaceListing> findByIdAndDeletedAtIsNull(UUID id);

    Optional<MarketplaceListing> findBySlugAndDeletedAtIsNull(String slug);

    long countBySellerUserIdAndStatusAndDeletedAtIsNull(UUID sellerUserId, ListingStatus status);

    List<MarketplaceListing> findByStatusAndExpiresAtBeforeAndDeletedAtIsNull(ListingStatus status, Instant cutoff);

    Page<MarketplaceListing> findBySellerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID sellerUserId, Pageable pageable);

    boolean existsBySlug(String slug);
}
