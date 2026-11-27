package com.analoghub.platform.labworkflow.dto;

import java.util.List;

public record ConfirmScanBatchRequest(
        List<ConfirmedFrameMetadata> frames
) {
    public record ConfirmedFrameMetadata(
            int frameNumber,
            String s3KeyOriginal,
            Integer widthPx,
            Integer heightPx,
            Long sizeBytes,
            String sha256Checksum
    ) {}
}