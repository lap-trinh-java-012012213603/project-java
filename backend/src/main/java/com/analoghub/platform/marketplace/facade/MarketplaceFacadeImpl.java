package com.analoghub.platform.marketplace.facade;

import com.analoghub.platform.marketplace.dto.MarketplaceListingDto;
import com.analoghub.platform.marketplace.dto.MarketplaceOrderDto;
import com.analoghub.platform.marketplace.service.MarketplaceListingService;
import com.analoghub.platform.marketplace.service.MarketplaceOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class MarketplaceFacadeImpl implements MarketplaceFacade {

    private final MarketplaceListingService listingService;
    private final MarketplaceOrderService orderService;

    @Override
    public Optional<MarketplaceListingDto> getListingById(UUID listingId) {
        return listingService.findListingDtoById(listingId);
    }

    @Override
    public Optional<MarketplaceOrderDto> getOrderById(UUID marketOrderId) {
        return orderService.findOrderDtoById(marketOrderId);
    }

    @Override
    public void markOrderDisputed(UUID marketOrderId, UUID disputeId) {
        orderService.markOrderDisputed(marketOrderId, disputeId);
    }

    @Override
    public void resolveOrderDispute(UUID marketOrderId, String resolutionOutcome) {
        orderService.resolveOrderDispute(marketOrderId, resolutionOutcome);
    }

    @Override
    public void hideListingByAdmin(UUID listingId, String reason) {
        listingService.hideListingByAdmin(listingId, reason);
    }
}
