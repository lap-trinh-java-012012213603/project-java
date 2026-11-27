package com.analoghub.platform.digitalarchive.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ArchiveRollDetailResponse(
        UUID id,
        UUID ownerUserId,
        UUID albumId,
        UUID sourceFilmRollId,
        UUID sourceOrderId,
        UUID sourceLabId,
        String rollTitle,
        String cameraModel,
        String lensModel,
        Integer shotIso,
        LocalDate shotDate,
        String processType,
        String scanScannerModel,
        String contactSheetUrl,
        int frameCount,
        long totalSizeBytes,
        OffsetDateTime rawExpiresAt,
        OffsetDateTime createdAt,
        List<FrameDto> frames
) {
    public record FrameDto(
            UUID id,
            int frameNumber,
            String webUrl,
            String thumbUrl,
            Integer widthPx,
            Integer heightPx,
            long sizeBytes,
            String qcStatus,
            BigDecimal qcOverallScore,
            Map<String, Object> exifMetadata,
            boolean isFavorite
    ) {}
}