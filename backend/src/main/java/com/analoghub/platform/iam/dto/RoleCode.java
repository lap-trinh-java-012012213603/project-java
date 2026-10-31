package com.analoghub.platform.iam.dto;

public enum RoleCode {
    CUSTOMER,
    LAB_OWNER,
    EXPERT,
    DELIVERY_PARTNER,
    ADMIN;

    public boolean isUpgradableRole() {
        return this == LAB_OWNER || this == EXPERT || this == DELIVERY_PARTNER;
    }
}
