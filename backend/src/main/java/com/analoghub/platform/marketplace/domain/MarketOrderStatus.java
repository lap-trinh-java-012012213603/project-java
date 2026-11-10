package com.analoghub.platform.marketplace.domain;

public enum MarketOrderStatus {
    PENDING_PAYMENT,
    ESCROW_HELD_AWAITING_SHIP,
    SHIPPED,
    DELIVERED_INSPECTING,
    COMPLETED,
    CANCELLED,
    DISPUTED,
    REFUNDED
}
