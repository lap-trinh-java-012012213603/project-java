package com.analoghub.platform.aiassistant.listener;

import com.analoghub.platform.aiassistant.service.cv.ScanQualityCvService;
import com.analoghub.platform.aiassistant.service.cv.ScanQualityCvService.RollCascadeSummary;
import com.analoghub.platform.digitalarchive.event.ArchiveRollCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens to ArchiveRollCreatedEvent and triggers the 2-Stage Scan Quality Cascade Pipeline
 * (Stage 1 CPU batch for all frames -> Stage 2 Vision LLM for up to 3 worst frames -> RollAnomalyAggregator)
 * (Section 1 of 04-scan-quality-cv-pipeline.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveScanQualityListener {

    private final ScanQualityCvService scanQualityCvService;

    @ApplicationModuleListener
    public void onArchiveRollCreated(ArchiveRollCreatedEvent event) {
        if (event == null || event.archiveRollId() == null) {
            return;
        }
        RollCascadeSummary summary = scanQualityCvService.analyzeRollCascade(event);
        log.info("Completed 2-Stage Scan QC cascade for archiveRollId={}: frames={}, stage2Escalated={}, avgScore={}, status={}, elapsedMs={}",
                event.archiveRollId(),
                summary.totalFramesAnalyzed(),
                summary.stage2EscalatedCount(),
                summary.averageQualityScore(),
                summary.overallRollStatus(),
                summary.totalExecutionTimeMs());
    }
}
