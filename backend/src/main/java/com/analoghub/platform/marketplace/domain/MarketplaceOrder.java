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
@Table(name = "mkt_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceOrder extends BaseAuditEntity {

    @Column(name = "order_code", length = 64, nullable = false, unique = true)
    private String orderCode;

    @Column(name = "buyer_user_id", nullable = false)
    private UUID buyerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private UUID sellerUserId;

    @Column(name = "accepted_offer_id")
    private UUID acceptedOfferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 32, nullable = false)
    private MarketOrderType orderType;

    @Column(name = "item_total_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal itemTotalVnd;

    @Column(name = "shipping_fee_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal shippingFeeVnd;

    @Column(name = "total_payable_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalPayableVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private MarketOrderStatus status;

    @Column(name = "inspection_deadline_at")
    private Instant inspectionDeadlineAt;
}
