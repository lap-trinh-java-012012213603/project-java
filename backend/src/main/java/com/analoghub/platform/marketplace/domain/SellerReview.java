package com.analoghub.platform.marketplace.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "mkt_seller_reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerReview extends BaseAuditEntity {

    @Column(name = "market_order_id", nullable = false, unique = true)
    private UUID marketOrderId;

    @Column(name = "reviewer_user_id", nullable = false)
    private UUID reviewerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private UUID sellerUserId;

    @Column(name = "rating", nullable = false)
    private Short rating;

    @Column(name = "item_as_described", nullable = false)
    private boolean itemAsDescribed;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;
}
