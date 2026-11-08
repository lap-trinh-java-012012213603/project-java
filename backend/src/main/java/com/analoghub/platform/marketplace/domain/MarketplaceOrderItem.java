package com.analoghub.platform.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "mkt_order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "market_order_id", nullable = false)
    private UUID marketOrderId;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "title_snapshot", length = 255, nullable = false)
    private String titleSnapshot;

    @Column(name = "agreed_price_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal agreedPriceVnd;
}
