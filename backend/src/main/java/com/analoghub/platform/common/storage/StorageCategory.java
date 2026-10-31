package com.analoghub.platform.common.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@Getter
@AllArgsConstructor
public enum StorageCategory {

    // === NHÓM PUBLIC (Trả về URL tĩnh trực tiếp) ===
    AVATAR("public/avatars", true, 2L * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/webp")),
    LAB_MEDIA("public/labs", true, 5L * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/webp")),
    FILM_STOCK("public/film-stocks", true, 2L * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/webp")),
    MARKETPLACE("public/marketplace", true, 5L * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/webp")),
    COMMUNITY("public/community", true, 5L * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/webp")),

    // === NHÓM PRIVATE MULTI-TENANT (Bắt buộc ký Presigned GET URL khi xem/tải) ===
    ROLL_SCAN_THUMB("private/tenants", false, 1L * 1024 * 1024, Set.of("image/jpeg", "image/webp")),
    ROLL_SCAN_PREVIEW("private/tenants", false, 5L * 1024 * 1024, Set.of("image/jpeg", "image/webp")),
    ROLL_SCAN_ORIGINAL("private/tenants", false, 25L * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/webp", "image/tiff"));

    private final String basePrefix;
    private final boolean publicAccess;
    private final long maxSizeBytes;
    private final Set<String> allowedContentTypes;
}