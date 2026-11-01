package com.analoghub.platform.marketplace.facade;

import com.analoghub.platform.marketplace.dto.MarketplaceListingDto;
import com.analoghub.platform.marketplace.dto.MarketplaceOrderDto;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceFacade {

    Optional<MarketplaceListingDto> getListingById(UUID listingId);

    Optional<MarketplaceOrderDto> getOrderById(UUID marketOrderId);

    void markOrderDisputed(UUID marketOrderId, UUID disputeId);

    void resolveOrderDispute(UUID marketOrderId, String resolutionOutcome);

    void hideListingByAdmin(UUID listingId, String reason);
}
