package com.analoghub.platform.payment.domain;

public enum WalletType {
    USER_WALLET,
    LAB_WALLET,
    SYSTEM_ESCROW,
    SYSTEM_REVENUE;

    public boolean isSystemWallet() {
        return this == SYSTEM_ESCROW || this == SYSTEM_REVENUE;
    }
}
