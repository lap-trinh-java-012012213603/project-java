package com.analoghub.platform.digitalarchive.facade;

import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.digitalarchive.domain.ArchiveFrame;
import com.analoghub.platform.digitalarchive.repository.ArchiveFrameRepository;
import com.analoghub.platform.digitalarchive.repository.ArchiveRollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DigitalArchiveFacadeImpl implements DigitalArchiveFacade {

    private final ArchiveRollRepository archiveRollRepository;
    private final ArchiveFrameRepository archiveFrameRepository;
    private final StorageService storageService;

    @Override
    @Transactional
    public void updateFrameQuality(UUID frameId, String qcStatus, BigDecimal qcOverallScore) {
        archiveFrameRepository.findById(frameId).ifPresent(frame -> {
            if (qcStatus != null && !qcStatus.isBlank()) {
                frame.setQcStatus(qcStatus);
            }
            if (qcOverallScore != null) {
                frame.setQcOverallScore(qcOverallScore);
            }
            archiveFrameRepository.save(frame);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArchiveRollSummary> getArchiveRollSummary(UUID archiveRollId) {
        return archiveRollRepository.findByIdAndDeletedAtIsNull(archiveRollId)
                .map(roll -> new ArchiveRollSummary(
                        roll.getId(),
                        roll.getOwnerUserId(),
                        roll.getRollTitle(),
                        roll.getCameraModel(),
                        roll.getLensModel(),
                        roll.getShotIso(),
                        roll.getProcessType(),
                        roll.getFrameCount(),
                        storageService.resolveReadUrl(roll.getContactSheetS3Key())
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArchiveFrameSummary> getArchiveFrameSummary(UUID frameId) {
        return archiveFrameRepository.findById(frameId)
                .map(this::toFrameSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArchiveFrameSummary> getRollFrames(UUID archiveRollId) {
        return archiveFrameRepository.findByArchiveRollIdOrderByFrameNumberAsc(archiveRollId)
                .stream()
                .map(this::toFrameSummary)
                .toList();
    }

    private ArchiveFrameSummary toFrameSummary(ArchiveFrame f) {
        return new ArchiveFrameSummary(
                f.getId(),
                f.getArchiveRollId(),
                f.getOwnerUserId(),
                f.getFrameNumber(),
                storageService.resolveReadUrl(f.getS3KeyWeb()),
                storageService.resolveReadUrl(f.getS3KeyThumb()),
                f.getQcStatus(),
                f.getQcOverallScore()
        );
    }
}