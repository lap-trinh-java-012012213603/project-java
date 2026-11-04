package com.analoghub.platform.payment.domain;

public enum EscrowStatus {
    HELD,
    PARTIALLY_REFUNDED,
    RELEASED_TO_PAYEE,
    REFUNDED_TO_PAYER,
    FROZEN_BY_DISPUTE,
    SPLIT_SETTLED;

    public boolean isReleasableOrRefundable() {
        return this == HELD || this == PARTIALLY_REFUNDED;
    }

    public boolean isReleasable() {
        return this == HELD || this == PARTIALLY_REFUNDED;
    }

    public boolean isTerminal() {
        return this == RELEASED_TO_PAYEE || this == REFUNDED_TO_PAYER || this == SPLIT_SETTLED;
    }
}
