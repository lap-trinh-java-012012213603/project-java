package com.analoghub.platform.payment.domain;

public enum LedgerEntryType {
    ESCROW_IN,
    ESCROW_RELEASE,
    PLATFORM_FEE,
    BLANK_ROLL_REFUND,
    FULL_REFUND,
    PAYOUT_LOCK,
    PAYOUT_SETTLE
}
