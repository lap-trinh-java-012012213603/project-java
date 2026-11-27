package com.analoghub.platform.admin.dto;

public enum DisputeType {
    LAB_ORDER_DISPUTE,
    MARKETPLACE_ORDER_DISPUTE;

    public String toEscrowReferenceType() {
        return this == LAB_ORDER_DISPUTE ? "LAB_ORDER" : "MARKETPLACE_ORDER";
    }
}
