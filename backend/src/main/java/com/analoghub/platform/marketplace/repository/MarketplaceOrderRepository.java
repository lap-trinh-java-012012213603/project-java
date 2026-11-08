package com.analoghub.platform.marketplace.repository;

import com.analoghub.platform.marketplace.domain.MarketOrderStatus;
import com.analoghub.platform.marketplace.domain.MarketplaceOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, UUID> {

    Page<MarketplaceOrder> findByBuyerUserIdOrderByCreatedAtDesc(UUID buyerUserId, Pageable pageable);

    Page<MarketplaceOrder> findByBuyerUserIdAndStatusOrderByCreatedAtDesc(
            UUID buyerUserId,
            MarketOrderStatus status,
            Pageable pageable
    );

    Page<MarketplaceOrder> findBySellerUserIdOrderByCreatedAtDesc(UUID sellerUserId, Pageable pageable);

    Page<MarketplaceOrder> findBySellerUserIdAndStatusOrderByCreatedAtDesc(
            UUID sellerUserId,
            MarketOrderStatus status,
            Pageable pageable
    );

    long countBySellerUserIdAndStatus(UUID sellerUserId, MarketOrderStatus status);
}
