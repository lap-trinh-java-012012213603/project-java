package com.analoghub.platform.digitalarchive.facade;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DigitalArchiveFacade {

    void updateFrameQuality(UUID frameId, String qcStatus, BigDecimal qcOverallScore);

    Optional<ArchiveRollSummary> getArchiveRollSummary(UUID archiveRollId);

    Optional<ArchiveFrameSummary> getArchiveFrameSummary(UUID frameId);

    record ArchiveRollSummary(
            UUID id,
            UUID ownerUserId,
            String rollTitle,
            String cameraModel,
            String lensModel,
            Integer shotIso,
            String processType,
            int frameCount,
            String contactSheetUrl
    ) {}

    record ArchiveFrameSummary(
            UUID id,
            UUID archiveRollId,
            UUID ownerUserId,
            int frameNumber,
            String webUrl,
            String thumbUrl,
            String qcStatus,
            BigDecimal qcOverallScore
    ) {}

    default List<ArchiveFrameSummary> getRollFrames(UUID archiveRollId) {
        return List.of();
    }
}