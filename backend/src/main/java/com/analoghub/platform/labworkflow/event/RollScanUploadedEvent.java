package com.analoghub.platform.labworkflow.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RollScanUploadedEvent(
        UUID batchId,
        UUID filmRollId,
        UUID orderId,
        UUID labId,
        UUID customerId,
        String rollCode,
        String processType,
        List<UploadedFrameInfo> frames,
        OffsetDateTime uploadedAt
) {
    public record UploadedFrameInfo(
            int frameNumber,
            String s3KeyOriginal,
            String s3KeyWeb,
            String s3KeyThumb,
            Integer widthPx,
            Integer heightPx,
            long sizeBytes,
            String sha256Checksum
    ) {}
}