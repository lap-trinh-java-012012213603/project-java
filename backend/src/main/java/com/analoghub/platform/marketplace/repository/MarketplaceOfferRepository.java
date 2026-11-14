package com.analoghub.platform.marketplace.repository;

import com.analoghub.platform.marketplace.domain.MarketplaceOffer;
import com.analoghub.platform.marketplace.domain.OfferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceOfferRepository extends JpaRepository<MarketplaceOffer, UUID> {

    boolean existsByListingIdAndBuyerUserIdAndStatusIn(
            UUID listingId,
            UUID buyerUserId,
            Collection<OfferStatus> statuses
    );

    long countByListingIdAndStatusIn(UUID listingId, Collection<OfferStatus> statuses);

    Optional<MarketplaceOffer> findFirstByListingIdAndBuyerUserIdAndStatusInOrderByCreatedAtDesc(
            UUID listingId,
            UUID buyerUserId,
            Collection<OfferStatus> statuses
    );

    List<MarketplaceOffer> findByListingIdAndStatusIn(UUID listingId, Collection<OfferStatus> statuses);

    Page<MarketplaceOffer> findByListingIdOrderByCreatedAtDesc(UUID listingId, Pageable pageable);

    Page<MarketplaceOffer> findByListingIdAndStatusOrderByCreatedAtDesc(
            UUID listingId,
            OfferStatus status,
            Pageable pageable
    );

    Page<MarketplaceOffer> findByListingIdAndBuyerUserIdOrderByCreatedAtDesc(
            UUID listingId,
            UUID buyerUserId,
            Pageable pageable
    );

    Page<MarketplaceOffer> findByListingIdAndBuyerUserIdAndStatusOrderByCreatedAtDesc(
            UUID listingId,
            UUID buyerUserId,
            OfferStatus status,
            Pageable pageable
    );
}
