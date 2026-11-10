package com.analoghub.platform.marketplace.repository;

import com.analoghub.platform.marketplace.domain.MarketplaceOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceOrderItemRepository extends JpaRepository<MarketplaceOrderItem, UUID> {

    List<MarketplaceOrderItem> findByMarketOrderId(UUID marketOrderId);

    List<MarketplaceOrderItem> findByMarketOrderIdIn(Collection<UUID> marketOrderIds);
}
