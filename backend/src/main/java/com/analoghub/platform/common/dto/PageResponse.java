package com.analoghub.platform.common.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> item,
        int page,
        int size,
        int totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(List<T> item, int page, int size, int totalElements, int totalPages) {
        return new PageResponse<T>(item, page, size, totalElements, totalPages);
    }
}
