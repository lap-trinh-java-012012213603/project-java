package com.analoghub.platform.aiassistant.service.cv;

import com.analoghub.platform.aiassistant.service.cv.ClassicalCvAnalyzer.AnomalyBlob;
import com.analoghub.platform.aiassistant.service.cv.ClassicalCvAnalyzer.Stage1AnalysisResult;
import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Roll-Level Spatial Recurrence Anomaly Aggregator (Section 6 of 04-scan-quality-cv-pipeline.md):
 * Detects scratches recurring at the same y-coordinate across >= 5 consecutive frames (99% confidence
 * of a stuck sand particle / dried residue in the Lab's film transport gate) and publishes a realtime
 * STOMP alert to /topic/labs/{labId}/kanban.
 */
@Slf4j
@Service
public class RollAnomalyAggregator {

    public static final int MIN_CONSECUTIVE_FRAMES_FOR_SYSTEMATIC_SCRATCH = 5;
    public static final double NORMALIZED_Y_TOLERANCE = 0.035;
    public static final double SYSTEMATIC_SCRATCH_CONFIDENCE = 0.99;

    @Builder
    public record FrameStage1Entry(
            UUID frameId,
            int frameNumber,
            Stage1AnalysisResult stage1Result
    ) {
    }

    @Builder
    public record RollAggregationResult(
            boolean systematicScratchDetected,
            Double recurringNormalizedY,
            int maxConsecutiveScratchFrames,
            Set<UUID> affectedFrameIds,
            double attributionConfidence,
            String alertMessageVi
    ) {
    }

    private final StompNotificationPublisher stompPublisher;

    @Autowired
    public RollAnomalyAggregator(@Autowired(required = false) StompNotificationPublisher stompPublisher) {
        this.stompPublisher = stompPublisher;
    }

    public RollAnomalyAggregator() {
        this(null);
    }

    public RollAggregationResult aggregateAndNotify(
            UUID archiveRollId,
            UUID labId,
            String rollCode,
            List<FrameStage1Entry> frameEntries
    ) {
        if (frameEntries == null || frameEntries.size() < MIN_CONSECUTIVE_FRAMES_FOR_SYSTEMATIC_SCRATCH) {
            return RollAggregationResult.builder()
                    .systematicScratchDetected(false)
                    .recurringNormalizedY(null)
                    .maxConsecutiveScratchFrames(0)
                    .affectedFrameIds(Set.of())
                    .attributionConfidence(0.0)
                    .alertMessageVi(null)
                    .build();
        }

        List<FrameStage1Entry> sorted = new ArrayList<>(frameEntries);
        sorted.sort(Comparator.comparingInt(FrameStage1Entry::frameNumber));

        // Collect candidate y-coordinates from all detected scratches
        List<Double> candidateYPositions = new ArrayList<>();
        for (FrameStage1Entry entry : sorted) {
            if (entry.stage1Result() == null || entry.stage1Result().detectedAnomalies() == null) {
                continue;
            }
            for (AnomalyBlob blob : entry.stage1Result().detectedAnomalies()) {
                if ("SCRATCH".equalsIgnoreCase(blob.type())) {
                    candidateYPositions.add(blob.normalizedY());
                }
            }
        }

        int bestStreak = 0;
        Double bestNormalizedY = null;
        Set<UUID> bestAffectedFrames = Set.of();

        for (double targetY : candidateYPositions) {
            int currentStreak = 0;
            int maxStreakForTarget = 0;
            Integer prevFrameNum = null;
            Set<UUID> currentRunIds = new LinkedHashSet<>();
            Set<UUID> bestRunIdsForTarget = new LinkedHashSet<>();

            for (FrameStage1Entry entry : sorted) {
                boolean hasScratchAtY = hasScratchNearY(entry.stage1Result(), targetY);
                if (hasScratchAtY) {
                    if (prevFrameNum == null || entry.frameNumber() == prevFrameNum + 1) {
                        currentStreak++;
                    } else {
                        currentStreak = 1;
                        currentRunIds.clear();
                    }
                    currentRunIds.add(entry.frameId());
                    prevFrameNum = entry.frameNumber();

                    if (currentStreak > maxStreakForTarget) {
                        maxStreakForTarget = currentStreak;
                        bestRunIdsForTarget = new LinkedHashSet<>(currentRunIds);
                    }
                } else {
                    currentStreak = 0;
                    currentRunIds.clear();
                    prevFrameNum = null;
                }
            }

            if (maxStreakForTarget > bestStreak) {
                bestStreak = maxStreakForTarget;
                bestNormalizedY = targetY;
                bestAffectedFrames = bestRunIdsForTarget;
            }
        }

        if (bestStreak < MIN_CONSECUTIVE_FRAMES_FOR_SYSTEMATIC_SCRATCH) {
            return RollAggregationResult.builder()
                    .systematicScratchDetected(false)
                    .recurringNormalizedY(null)
                    .maxConsecutiveScratchFrames(bestStreak)
                    .affectedFrameIds(Set.of())
                    .attributionConfidence(0.0)
                    .alertMessageVi(null)
                    .build();
        }

        String displayRollCode = StringUtils.hasText(rollCode)
                ? rollCode
                : (archiveRollId != null ? archiveRollId.toString().substring(0, 8) : "ROLL");

        String alertMessageVi = String.format(
                "Cảnh báo: Cuộn film %s phát hiện vệt xước cố định trên khay scan, kỹ thuật viên vui lòng dừng máy để vệ sinh trục cuốn.",
                displayRollCode
        );

        if (labId != null && stompPublisher != null) {
            try {
                stompPublisher.publishToTopic(
                        "/topic/labs/" + labId + "/kanban",
                        Map.of(
                                "type", "SCANNER_SCRATCH_ANOMALY_ALERT",
                                "archiveRollId", archiveRollId != null ? archiveRollId.toString() : "",
                                "rollCode", displayRollCode,
                                "consecutiveFramesCount", bestStreak,
                                "confidence", SYSTEMATIC_SCRATCH_CONFIDENCE,
                                "messageVi", alertMessageVi
                        )
                );
            } catch (Exception ex) {
                log.warn("Failed to publish STOMP scanner scratch alert for labId={}: {}", labId, ex.getMessage());
            }
        }

        return RollAggregationResult.builder()
                .systematicScratchDetected(true)
                .recurringNormalizedY(bestNormalizedY)
                .maxConsecutiveScratchFrames(bestStreak)
                .affectedFrameIds(Set.copyOf(bestAffectedFrames))
                .attributionConfidence(SYSTEMATIC_SCRATCH_CONFIDENCE)
                .alertMessageVi(alertMessageVi)
                .build();
    }

    private static boolean hasScratchNearY(Stage1AnalysisResult stage1, double targetY) {
        if (stage1 == null || stage1.detectedAnomalies() == null) {
            return false;
        }
        for (AnomalyBlob blob : stage1.detectedAnomalies()) {
            if ("SCRATCH".equalsIgnoreCase(blob.type())
                    && Math.abs(blob.normalizedY() - targetY) <= NORMALIZED_Y_TOLERANCE) {
                return true;
            }
        }
        return false;
    }
}
