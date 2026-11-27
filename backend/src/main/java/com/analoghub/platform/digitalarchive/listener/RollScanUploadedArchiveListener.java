package com.analoghub.platform.digitalarchive.listener;

import com.analoghub.platform.digitalarchive.domain.ArchiveFrame;
import com.analoghub.platform.digitalarchive.domain.ArchiveRoll;
import com.analoghub.platform.digitalarchive.event.ArchiveRollCreatedEvent;
import com.analoghub.platform.digitalarchive.repository.ArchiveFrameRepository;
import com.analoghub.platform.digitalarchive.repository.ArchiveRollRepository;
import com.analoghub.platform.digitalarchive.service.ContactSheetGeneratorService;
import com.analoghub.platform.labworkflow.event.RollScanUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RollScanUploadedArchiveListener {

    private final ArchiveRollRepository archiveRollRepository;
    private final ArchiveFrameRepository archiveFrameRepository;
    private final ContactSheetGeneratorService contactSheetGeneratorService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    @Transactional
    public void onRollScanUploaded(RollScanUploadedEvent event) {
        log.info("Khởi tạo Digital Archive cho filmRollId={}, rollCode={}", event.filmRollId(), event.rollCode());

        ArchiveRoll roll = archiveRollRepository.findBySourceFilmRollId(event.filmRollId())
                .orElseGet(ArchiveRoll::new);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        roll.setOwnerUserId(event.customerId());
        roll.setSourceFilmRollId(event.filmRollId());
        roll.setSourceOrderId(event.orderId());
        roll.setSourceLabId(event.labId());
        roll.setRollTitle("Roll " + event.rollCode());
        roll.setProcessType(event.processType());
        roll.setFrameCount(event.frames() != null ? event.frames().size() : 0);

        long totalBytes = event.frames() == null ? 0L :
                event.frames().stream().mapToLong(RollScanUploadedEvent.UploadedFrameInfo::sizeBytes).sum();
        roll.setTotalSizeBytes(totalBytes);
        roll.setRawExpiresAt(now.plusDays(30));

        ArchiveRoll savedRoll = archiveRollRepository.save(roll);

        List<ArchiveFrame> framesToSave = new ArrayList<>();
        if (event.frames() != null) {
            for (RollScanUploadedEvent.UploadedFrameInfo f : event.frames()) {
                ArchiveFrame frame = archiveFrameRepository
                        .findByArchiveRollIdAndFrameNumber(savedRoll.getId(), (short) f.frameNumber())
                        .orElseGet(ArchiveFrame::new);

                frame.setArchiveRollId(savedRoll.getId());
                frame.setOwnerUserId(event.customerId());
                frame.setFrameNumber((short) f.frameNumber());
                frame.setS3KeyOriginal(f.s3KeyOriginal());
                frame.setS3KeyWeb(f.s3KeyWeb());
                frame.setS3KeyThumb(f.s3KeyThumb());
                frame.setWidthPx(f.widthPx());
                frame.setHeightPx(f.heightPx());
                frame.setSizeBytes(f.sizeBytes());
                frame.setSha256Checksum(f.sha256Checksum());
                frame.setQcStatus("PENDING_AI");
                framesToSave.add(frame);
            }
        }
        List<ArchiveFrame> savedFrames = archiveFrameRepository.saveAll(framesToSave);

        // Dựng Contact Sheet tự động (C51)
        contactSheetGeneratorService.generateContactSheetForRoll(savedRoll.getId());

        // Phát ArchiveRollCreatedEvent sang module aiassistant (C50)
        List<ArchiveRollCreatedEvent.FrameRef> frameRefs = savedFrames.stream()
                .map(f -> new ArchiveRollCreatedEvent.FrameRef(
                        f.getId(),
                        f.getFrameNumber(),
                        f.getS3KeyWeb(),
                        f.getS3KeyThumb()
                ))
                .toList();

        eventPublisher.publishEvent(new ArchiveRollCreatedEvent(
                savedRoll.getId(),
                savedRoll.getSourceFilmRollId(),
                savedRoll.getOwnerUserId(),
                savedRoll.getSourceLabId(),
                savedFrames.size(),
                frameRefs,
                now
        ));
    }
}