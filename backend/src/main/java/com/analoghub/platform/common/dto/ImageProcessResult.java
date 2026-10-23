package com.analoghub.platform.common.dto;

public record ImageProcessResult(
        byte[] previewBytes,
        byte[] thumbnailBytes,
        int originalWidth,
        int originalHeight,
        String format
) {
}
