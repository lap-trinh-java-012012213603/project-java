package com.analoghub.platform.labworkflow.dto;

import java.util.List;
import java.util.UUID;

public record InitiateScanBatchResponse(
        UUID batchId,
        UUID filmRollId,
        String status,
        int expectedFrameCount,
        String s3Prefix,
        List<FramePresignedUrlDto> frameUploadUrls
) {
    public record FramePresignedUrlDto(
            int frameNumber,
            String s3KeyOriginal,
            String uploadPutUrl,
            long expiresInSeconds
    ) {}
}