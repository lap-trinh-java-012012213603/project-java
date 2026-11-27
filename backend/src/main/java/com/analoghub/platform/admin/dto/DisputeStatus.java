package com.analoghub.platform.admin.dto;

import java.util.Locale;

public enum DisputeStatus {
    OPENED,
    AWAITING_RESPONDENT_EVIDENCE,
    UNDER_ADMIN_REVIEW,
    RESOLVED_REFUND_BUYER,
    RESOLVED_RELEASE_SELLER,
    RESOLVED_SPLIT;

    public static boolean isResolvedStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("RESOLVED_")
                || "RESOLVED".equals(normalized)
                || "REJECTED".equals(normalized);
    }
}
