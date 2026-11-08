package com.analoghub.platform.marketplace.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mkt_offers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceOffer extends BaseAuditEntity {

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "buyer_user_id", nullable = false)
    private UUID buyerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private UUID sellerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_type", length = 32, nullable = false)
    private OfferType offerType;

    @Column(name = "offered_price_vnd", precision = 15, scale = 2)
    private BigDecimal offeredPriceVnd;

    @Column(name = "swap_offered_listing_id")
    private UUID swapOfferedListingId;

    @Column(name = "swap_item_description", columnDefinition = "text")
    private String swapItemDescription;

    @Column(name = "cash_topup_vnd", precision = 15, scale = 2)
    private BigDecimal cashTopupVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private OfferStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
