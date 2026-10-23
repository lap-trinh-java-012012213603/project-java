package com.analoghub.platform.common.dto;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "SUCCESS", "Thanh cong", data, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "SUCCESS", message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, Instant.now());
    }
}
