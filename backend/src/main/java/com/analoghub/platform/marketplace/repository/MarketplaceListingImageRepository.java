package com.analoghub.platform.marketplace.repository;

import com.analoghub.platform.marketplace.domain.MarketplaceListingImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceListingImageRepository extends JpaRepository<MarketplaceListingImage, UUID> {

    List<MarketplaceListingImage> findByListingIdOrderBySortOrderAsc(UUID listingId);

    List<MarketplaceListingImage> findByListingIdInOrderBySortOrderAsc(Collection<UUID> listingIds);

    void deleteByListingId(UUID listingId);
}
