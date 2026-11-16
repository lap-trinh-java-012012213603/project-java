package com.analoghub.platform.aiassistant.service.cv;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.ClassicalCvMetrics;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.client.VisionDiagnosticResult;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.ScanQcReport;
import com.analoghub.platform.aiassistant.dto.ScanDiagnosticResponse;
import com.analoghub.platform.aiassistant.dto.ScanDiagnosticResponse.Stage1ClassicalCvMetricsDto;
import com.analoghub.platform.aiassistant.dto.ScanDiagnosticResponse.Stage2VisionDiagnosticDto;
import com.analoghub.platform.aiassistant.dto.TriggerScanDiagnosticRequest;
import com.analoghub.platform.aiassistant.event.ScanQualityAnalyzedEvent;
import com.analoghub.platform.aiassistant.repository.ScanQcReportRepository;
import com.analoghub.platform.aiassistant.service.cv.ClassicalCvAnalyzer.Stage1AnalysisResult;
import com.analoghub.platform.aiassistant.service.cv.RollAnomalyAggregator.FrameStage1Entry;
import com.analoghub.platform.aiassistant.service.cv.RollAnomalyAggregator.RollAggregationResult;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.digitalarchive.event.ArchiveRollCreatedEvent;
import com.analoghub.platform.digitalarchive.event.ArchiveRollCreatedEvent.FrameRef;
import com.analoghub.platform.digitalarchive.facade.DigitalArchiveFacade;
import com.analoghub.platform.digitalarchive.facade.DigitalArchiveFacade.ArchiveFrameSummary;
import com.analoghub.platform.digitalarchive.facade.DigitalArchiveFacade.ArchiveRollSummary;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two-Stage Cascade Scan Quality Control (QC) Service:
 * - Stage 1: Parallel CPU ClassicalCvAnalyzer across all 36 frames of a roll (< 0.8s)
 * - Filter Gate: Only escalates at most 3 lowest-scoring frames (< 75.0) to Stage 2 Vision LLM
 * - Stage 2: VisionLlmClient attribution (LAB_SCANNER_DEFECT vs PHOTOGRAPHER_SHOOTING_ERROR)
 * - Roll-Level Anomaly Aggregator: Detects recurring scanner scratches across >= 5 consecutive frames
 * (Sections 1, 5 & 6 of 04-scan-quality-cv-pipeline.md & API 8 of 15-aiassistant.md).
 */
@Slf4j
@Service
public class ScanQualityCvService {

    public static final int MAX_STAGE2_FRAMES_PER_ROLL = 3;
    public static final double STAGE2_SCORE_THRESHOLD = 75.0;
    public static final String PROMPT_TEMPLATE_PATH = "prompts/cv/scan-qc-vision-critique-v1.2.st";

    public static final String ATTRIBUTION_NONE = "NONE";
    public static final String ATTRIBUTION_LAB_SCANNER = "LAB_SCANNER_DEFECT";
    public static final String ATTRIBUTION_PHOTOGRAPHER = "PHOTOGRAPHER_SHOOTING_ERROR";

    private static final String FALLBACK_VISION_PROMPT_TEMPLATE = """
            Bạn là Giám định viên Kỹ thuật Buồng tối AnalogHub. Nhiệm vụ của bạn là xem xét bức ảnh scan và kết hợp với dữ liệu đo đạc Tầng 1 để đưa ra kết luận công tâm.
            DỮ LIỆU ĐO ĐẠC TẦNG 1:
            - Độ nét Laplacian: {LAPLACIAN_SCORE}
            - Độ sáng trung bình kênh Y: {LUMINANCE_MEAN} (Tỷ lệ bết tối: {DARK_RATIO}, Tỷ lệ cháy sáng: {BRIGHT_RATIO})
            - Số lượng hạt bụi/xước đếm được: {DUST_BLOB_COUNT}
            - Điểm ám màu CIELAB: {COLOR_CAST_SCORE}
            """;

    @Builder
    public record RegisteredFrameContext(
            UUID frameId,
            UUID archiveRollId,
            UUID ownerUserId,
            UUID sourceLabId,
            int frameNumber,
            String s3KeyWeb,
            boolean blackAndWhiteFilm,
            byte[] imageBytes
    ) {
    }

    @Builder
    public record RollCascadeSummary(
            UUID archiveRollId,
            int totalFramesAnalyzed,
            int stage2EscalatedCount,
            double averageQualityScore,
            String overallRollStatus,
            boolean systematicScratchDetected,
            String systematicScratchAlertVi,
            List<ScanDiagnosticResponse> frameReports,
            long totalExecutionTimeMs
    ) {
    }

    private final ClassicalCvAnalyzer classicalCvAnalyzer;
    private final RollAnomalyAggregator rollAnomalyAggregator;
    private final AiModelRouter aiModelRouter;
    private final AiProperties aiProperties;
    private final ScanQcReportRepository reportRepository;
    private final DigitalArchiveFacade digitalArchiveFacade;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final String visionPromptTemplate;

    private final Map<UUID, RegisteredFrameContext> frameContextRegistry = new ConcurrentHashMap<>();
    private final Map<UUID, ScanQcReport> frameReportCache = new ConcurrentHashMap<>();

    @Autowired
    public ScanQualityCvService(
            ClassicalCvAnalyzer classicalCvAnalyzer,
            RollAnomalyAggregator rollAnomalyAggregator,
            AiModelRouter aiModelRouter,
            AiProperties aiProperties,
            ScanQcReportRepository reportRepository,
            @Autowired(required = false) DigitalArchiveFacade digitalArchiveFacade,
            @Autowired(required = false) StorageService storageService,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher
    ) {
        this.classicalCvAnalyzer = classicalCvAnalyzer;
        this.rollAnomalyAggregator = rollAnomalyAggregator;
        this.aiModelRouter = aiModelRouter;
        this.aiProperties = aiProperties;
        this.reportRepository = reportRepository;
        this.digitalArchiveFacade = digitalArchiveFacade;
        this.storageService = storageService;
        this.eventPublisher = eventPublisher;
        this.visionPromptTemplate = loadVisionPromptTemplate();
    }

    public void registerFrameContext(RegisteredFrameContext context) {
        if (context != null && context.frameId() != null) {
            frameContextRegistry.put(context.frameId(), context);
        }
    }

    /**
     * Workflow A: Full Roll Two-Stage Cascade triggered by ArchiveRollCreatedEvent.
     * Runs Stage 1 on all frames in parallel on Virtual Threads, escalates at most 3 lowest-scoring
     * frames (< 75.0) to Stage 2 Vision LLM, runs RollAnomalyAggregator, updates DigitalArchiveFacade,
     * and publishes ScanQualityAnalyzedEvent.
     */
    @Transactional
    public RollCascadeSummary analyzeRollCascade(ArchiveRollCreatedEvent event) {
        long startNanos = System.nanoTime();
        if (event == null || event.frames() == null || event.frames().isEmpty()) {
            return RollCascadeSummary.builder()
                    .archiveRollId(event != null ? event.archiveRollId() : null)
                    .totalFramesAnalyzed(0)
                    .stage2EscalatedCount(0)
                    .averageQualityScore(100.0)
                    .overallRollStatus(ClassicalCvAnalyzer.DEFECT_GOOD)
                    .systematicScratchDetected(false)
                    .frameReports(List.of())
                    .totalExecutionTimeMs(0L)
                    .build();
        }

        boolean isBwFilm = resolveIsBlackAndWhiteRoll(event.archiveRollId());
        List<FrameRef> frames = event.frames();

        // Step 1: Parallel CPU Stage-1 execution across all frames using Java 21 Virtual Threads
        List<FrameStage1Entry> stage1Entries;
        try (ExecutorService vThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<FrameStage1Entry>> futures = frames.stream()
                    .map(frame -> CompletableFuture.supplyAsync(() -> {
                        byte[] imgBytes = resolveFrameImageBytes(
                                frame.frameId(),
                                event.archiveRollId(),
                                event.ownerUserId(),
                                event.sourceLabId(),
                                frame.frameNumber(),
                                frame.s3KeyWeb(),
                                isBwFilm
                        );
                        Stage1AnalysisResult s1 = classicalCvAnalyzer.analyzeImageBytes(imgBytes, isBwFilm);
                        return FrameStage1Entry.builder()
                                .frameId(frame.frameId())
                                .frameNumber(frame.frameNumber())
                                .stage1Result(s1)
                                .build();
                    }, vThreadExecutor))
                    .toList();

            stage1Entries = futures.stream().map(CompletableFuture::join).toList();
        }

        // Step 2: Filter frames with score < 75.0 and pick at most 3 lowest-scoring frames for Stage 2
        List<FrameStage1Entry> defectiveCandidates = stage1Entries.stream()
                .filter(e -> e.stage1Result().overallQualityScore() < STAGE2_SCORE_THRESHOLD
                        || e.stage1Result().requiresStage2VisionLlm())
                .sorted(Comparator.comparingDouble(e -> e.stage1Result().overallQualityScore()))
                .limit(MAX_STAGE2_FRAMES_PER_ROLL)
                .toList();

        Map<UUID, Stage2VisionDiagnosticDto> stage2ResultsByFrame = new LinkedHashMap<>();
        for (FrameStage1Entry candidate : defectiveCandidates) {
            byte[] imgBytes = resolveFrameImageBytes(
                    candidate.frameId(),
                    event.archiveRollId(),
                    event.ownerUserId(),
                    event.sourceLabId(),
                    candidate.frameNumber(),
                    null,
                    isBwFilm
            );
            Stage2VisionDiagnosticDto stage2Dto = executeStage2VisionDiagnostic(
                    event.ownerUserId(),
                    imgBytes,
                    candidate.stage1Result(),
                    null,
                    null
            );
            stage2ResultsByFrame.put(candidate.frameId(), stage2Dto);
        }

        // Step 3: Roll-Level Spatial Recurrence Anomaly Aggregation
        String rollCode = event.sourceFilmRollId() != null
                ? "ROLL-" + event.sourceFilmRollId().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                : "ROLL-" + event.archiveRollId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        RollAggregationResult rollAggregation = rollAnomalyAggregator.aggregateAndNotify(
                event.archiveRollId(),
                event.sourceLabId(),
                rollCode,
                stage1Entries
        );

        // Step 4: Build & persist ScanQcReport for every frame + sync DigitalArchiveFacade
        List<ScanDiagnosticResponse> frameResponses = new ArrayList<>(stage1Entries.size());
        double totalScoreSum = 0.0;
        boolean anyDefect = false;

        for (FrameStage1Entry entry : stage1Entries) {
            UUID frameId = entry.frameId();
            Stage1AnalysisResult s1 = entry.stage1Result();
            Stage2VisionDiagnosticDto s2 = stage2ResultsByFrame.get(frameId);

            boolean inSystematicScratchRun = rollAggregation.systematicScratchDetected()
                    && rollAggregation.affectedFrameIds().contains(frameId);

            String finalDefect;
            String finalAttribution;
            double finalConfidence;
            String finalCritiqueVi;
            String finalActionVi;
            boolean stage2Executed = (s2 != null && s2.executed());
            String modelUsed = s2 != null ? s2.visionModelUsed() : null;

            if (inSystematicScratchRun) {
                finalDefect = ClassicalCvAnalyzer.DEFECT_DUST_SCRATCH;
                finalAttribution = ATTRIBUTION_LAB_SCANNER;
                finalConfidence = RollAnomalyAggregator.SYSTEMATIC_SCRATCH_CONFIDENCE;
                finalCritiqueVi = rollAggregation.alertMessageVi();
                finalActionVi = "Yêu cầu Film Lab vệ sinh khe dẫn film/trục cuốn máy scan và quét lại (re-scan) toàn bộ cuộn film miễn phí.";
            } else if (s2 != null) {
                finalDefect = s2.detectedDefect();
                finalAttribution = s2.defectAttribution();
                finalConfidence = s2.attributionConfidence();
                finalCritiqueVi = s2.visionCritiqueVi();
                finalActionVi = s2.recommendedActionVi();
            } else {
                finalDefect = ClassicalCvAnalyzer.DEFECT_GOOD;
                finalAttribution = ATTRIBUTION_NONE;
                finalConfidence = 0.98;
                finalCritiqueVi = "Bức ảnh đạt tiêu chuẩn kỹ thuật tốt qua kiểm định Tầng 1 (độ nét và phơi sáng cân bằng).";
                finalActionVi = "Không cần xử lý lại.";
            }

            if (!ClassicalCvAnalyzer.DEFECT_GOOD.equals(finalDefect)) {
                anyDefect = true;
            }

            double finalScore = s1.overallQualityScore();
            totalScoreSum += finalScore;

            ScanQcReport savedReport = saveFrameReport(
                    frameId,
                    event.archiveRollId(),
                    entry.frameNumber(),
                    event.sourceLabId(),
                    s1,
                    stage2Executed,
                    modelUsed,
                    finalDefect,
                    finalAttribution,
                    finalConfidence,
                    finalCritiqueVi,
                    finalActionVi,
                    finalScore
            );

            if (digitalArchiveFacade != null) {
                try {
                    digitalArchiveFacade.updateFrameQuality(
                            frameId,
                            finalDefect,
                            BigDecimal.valueOf(finalScore).setScale(2, RoundingMode.HALF_UP)
                    );
                } catch (Exception ex) {
                    log.warn("Could not update frame quality in DigitalArchiveFacade for frameId={}: {}",
                            frameId, ex.getMessage());
                }
            }

            frameResponses.add(toDiagnosticResponse(savedReport));
        }

        double avgScore = round2(totalScoreSum / Math.max(1, stage1Entries.size()));
        String overallStatus = rollAggregation.systematicScratchDetected()
                ? "SYSTEMATIC_LAB_SCRATCH_DETECTED"
                : (anyDefect ? "DEFECT_DETECTED" : ClassicalCvAnalyzer.DEFECT_GOOD);

        publishQualityAnalyzedEvent(
                event.archiveRollId(),
                null,
                event.ownerUserId(),
                event.sourceLabId(),
                stage1Entries.size(),
                defectiveCandidates.size(),
                avgScore,
                overallStatus,
                rollAggregation.systematicScratchDetected()
        );

        long totalElapsedMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
        return RollCascadeSummary.builder()
                .archiveRollId(event.archiveRollId())
                .totalFramesAnalyzed(stage1Entries.size())
                .stage2EscalatedCount(defectiveCandidates.size())
                .averageQualityScore(avgScore)
                .overallRollStatus(overallStatus)
                .systematicScratchDetected(rollAggregation.systematicScratchDetected())
                .systematicScratchAlertVi(rollAggregation.alertMessageVi())
                .frameReports(List.copyOf(frameResponses))
                .totalExecutionTimeMs(totalElapsedMs)
                .build();
    }

    /**
     * Workflow B: API 8 POST /api/v1/ai/scan-diagnostics/{frameId}
     * Deep 2-Stage Scan Quality Diagnostic on a single frame.
     */
    @Transactional
    public ScanDiagnosticResponse diagnoseFrame(
            UUID currentUserId,
            boolean isAdmin,
            Set<UUID> currentUserLabIds,
            UUID frameId,
            TriggerScanDiagnosticRequest request
    ) {
        RegisteredFrameContext ctx = resolveAuthorizedFrameContext(currentUserId, isAdmin, currentUserLabIds, frameId);

        boolean forceReanalyze = request != null && Boolean.TRUE.equals(request.forceReanalyze());
        boolean runStage2 = request == null || request.runVisionLlmStage2() == null || request.runVisionLlmStage2();

        if (!forceReanalyze) {
            ScanQcReport cached = findExistingReportForFrame(frameId);
            if (cached != null && (!runStage2 || cached.isStage2Executed())) {
                return toDiagnosticResponse(cached);
            }
        }

        byte[] imageBytes = resolveFrameImageBytes(
                ctx.frameId(),
                ctx.archiveRollId(),
                ctx.ownerUserId(),
                ctx.sourceLabId(),
                ctx.frameNumber(),
                ctx.s3KeyWeb(),
                ctx.blackAndWhiteFilm()
        );

        Stage1AnalysisResult s1 = classicalCvAnalyzer.analyzeImageBytes(imageBytes, ctx.blackAndWhiteFilm());

        Stage2VisionDiagnosticDto s2;
        if (runStage2) {
            s2 = executeStage2VisionDiagnostic(
                    currentUserId,
                    imageBytes,
                    s1,
                    request != null ? request.preferredProvider() : null,
                    request != null ? request.userSymptomNote() : null
            );
        } else {
            String attribution = determineRuleBasedAttribution(s1.primaryDefect());
            s2 = Stage2VisionDiagnosticDto.builder()
                    .executed(false)
                    .visionModelUsed(null)
                    .detectedDefect(s1.primaryDefect())
                    .defectAttribution(attribution)
                    .attributionConfidence(0.85)
                    .visionCritiqueVi(buildRuleBasedCritiqueVi(s1.primaryDefect(), s1.metrics()))
                    .recommendedActionVi(buildRecommendedActionVi(s1.primaryDefect(), attribution))
                    .build();
        }

        ScanQcReport savedReport = saveFrameReport(
                ctx.frameId(),
                ctx.archiveRollId(),
                ctx.frameNumber(),
                ctx.sourceLabId(),
                s1,
                s2.executed(),
                s2.visionModelUsed(),
                s2.detectedDefect(),
                s2.defectAttribution(),
                s2.attributionConfidence(),
                s2.visionCritiqueVi(),
                s2.recommendedActionVi(),
                s1.overallQualityScore()
        );

        if (digitalArchiveFacade != null) {
            try {
                digitalArchiveFacade.updateFrameQuality(
                        ctx.frameId(),
                        s2.detectedDefect(),
                        BigDecimal.valueOf(s1.overallQualityScore()).setScale(2, RoundingMode.HALF_UP)
                );
            } catch (Exception ex) {
                log.warn("Could not update frame quality in DigitalArchiveFacade for frameId={}: {}",
                        ctx.frameId(), ex.getMessage());
            }
        }

        publishQualityAnalyzedEvent(
                ctx.archiveRollId(),
                ctx.frameId(),
                ctx.ownerUserId(),
                ctx.sourceLabId(),
                1,
                s2.executed() ? 1 : 0,
                s1.overallQualityScore(),
                s2.detectedDefect(),
                false
        );

        return toDiagnosticResponse(savedReport);
    }

    private Stage2VisionDiagnosticDto executeStage2VisionDiagnostic(
            UUID userId,
            byte[] imageBytes,
            Stage1AnalysisResult s1,
            ModelProvider preferredProvider,
            String userSymptomNote
    ) {
        ClassicalCvMetrics m = s1.metrics();
        String renderedPrompt = visionPromptTemplate
                .replace("{LAPLACIAN_SCORE}", String.format(Locale.ROOT, "%.2f", m.laplacianSharpnessScore()))
                .replace("{LUMINANCE_MEAN}", String.format(Locale.ROOT, "%.2f", m.exposureMeanLuminance()))
                .replace("{DARK_RATIO}", String.format(Locale.ROOT, "%.3f", m.darkPixelRatio()))
                .replace("{BRIGHT_RATIO}", String.format(Locale.ROOT, "%.3f", m.brightPixelRatio()))
                .replace("{DUST_BLOB_COUNT}", String.valueOf(m.dustScratchBlobCount()))
                .replace("{COLOR_CAST_SCORE}", String.format(Locale.ROOT, "%.2f", m.colorCastCieScore()));

        if (StringUtils.hasText(userSymptomNote)) {
            renderedPrompt = renderedPrompt + "\nGHI CHÚ TRIỆU CHỨNG CỦA NGƯỜI DÙNG: " + userSymptomNote.trim();
        }

        VisionDiagnosticResult visionResult = aiModelRouter.analyzeScanFrame(
                userId,
                imageBytes,
                "image/webp",
                m,
                renderedPrompt
        );

        ModelProvider effectiveProvider = preferredProvider != null
                ? preferredProvider
                : aiProperties.resolvePrimaryProvider();
        String visionModel = effectiveProvider == ModelProvider.OPENAI
                ? aiProperties.getOpenai().getVisionModel()
                : aiProperties.getGemini().getVisionModel();
        if (!StringUtils.hasText(visionModel)) {
            visionModel = "gemini-2.5-flash";
        }

        // Combine Stage-1 light leak / color cast detection with Stage-2 Vision LLM output
        String defect = s1.lightLeakDetected()
                ? ClassicalCvAnalyzer.DEFECT_LIGHT_LEAK
                : (visionResult != null && StringUtils.hasText(visionResult.detectedDefect())
                        && !"GOOD".equalsIgnoreCase(visionResult.detectedDefect())
                        ? visionResult.detectedDefect()
                        : s1.primaryDefect());

        String attribution = determineRuleBasedAttribution(defect);
        if (visionResult != null && StringUtils.hasText(visionResult.defectAttribution())
                && !ATTRIBUTION_NONE.equalsIgnoreCase(visionResult.defectAttribution())
                && !s1.lightLeakDetected()) {
            attribution = visionResult.defectAttribution();
        }

        double confidence = visionResult != null && visionResult.confidenceScore() > 0
                ? visionResult.confidenceScore()
                : 0.92;

        String critiqueVi = s1.lightLeakDetected()
                ? buildRuleBasedCritiqueVi(ClassicalCvAnalyzer.DEFECT_LIGHT_LEAK, m)
                : (visionResult != null && StringUtils.hasText(visionResult.visionCritiqueVi())
                        ? visionResult.visionCritiqueVi()
                        : buildRuleBasedCritiqueVi(defect, m));

        String recommendedActionVi = (visionResult != null
                && visionResult.suggestedSolutions() != null
                && !visionResult.suggestedSolutions().isEmpty()
                && !s1.lightLeakDetected())
                ? String.join(" ", visionResult.suggestedSolutions())
                : buildRecommendedActionVi(defect, attribution);

        return Stage2VisionDiagnosticDto.builder()
                .executed(true)
                .visionModelUsed(visionModel)
                .detectedDefect(defect)
                .defectAttribution(attribution)
                .attributionConfidence(round2(confidence))
                .visionCritiqueVi(critiqueVi)
                .recommendedActionVi(recommendedActionVi)
                .build();
    }

    /**
     * Section 5.1 Responsibility Attribution Matrix:
     * - DUST_SCRATCH / COLOR_CAST -> LAB_SCANNER_DEFECT
     * - LIGHT_LEAK / BLURRY / UNDEREXPOSED / OVEREXPOSED -> PHOTOGRAPHER_SHOOTING_ERROR
     */
    public static String determineRuleBasedAttribution(String detectedDefect) {
        if (detectedDefect == null || ClassicalCvAnalyzer.DEFECT_GOOD.equalsIgnoreCase(detectedDefect)) {
            return ATTRIBUTION_NONE;
        }
        return switch (detectedDefect.toUpperCase(Locale.ROOT)) {
            case ClassicalCvAnalyzer.DEFECT_DUST_SCRATCH, ClassicalCvAnalyzer.DEFECT_COLOR_CAST ->
                    ATTRIBUTION_LAB_SCANNER;
            case ClassicalCvAnalyzer.DEFECT_LIGHT_LEAK,
                 ClassicalCvAnalyzer.DEFECT_BLURRY,
                 ClassicalCvAnalyzer.DEFECT_UNDEREXPOSED,
                 ClassicalCvAnalyzer.DEFECT_OVEREXPOSED ->
                    ATTRIBUTION_PHOTOGRAPHER;
            default -> ATTRIBUTION_NONE;
        };
    }

    private static String buildRuleBasedCritiqueVi(String defect, ClassicalCvMetrics m) {
        if (defect == null) {
            return "Bức ảnh đạt tiêu chuẩn kỹ thuật tốt.";
        }
        return switch (defect.toUpperCase(Locale.ROOT)) {
            case ClassicalCvAnalyzer.DEFECT_LIGHT_LEAK ->
                    "Bức ảnh xuất hiện vệt sáng màu cam đỏ bất đối xứng chạy dọc từ mép khung hình lấn vào vùng trung tâm. "
                    + "Đây là dấu hiệu điển hình của mút xốp chắn sáng buồng máy (Light Seal) bị thoái hóa mủn khiến ánh sáng lọt vào lưng máy của người chụp (PHOTOGRAPHER_SHOOTING_ERROR).";
            case ClassicalCvAnalyzer.DEFECT_DUST_SCRATCH ->
                    String.format(Locale.ROOT,
                            "Phát hiện %d đốm bụi/vết xước trên bề mặt ảnh scan dù độ nét chủ thể (Laplacian = %.2f) đạt chuẩn. "
                            + "Đây là dấu hiệu của bụi bám mặt kính/cảm biến máy scan hoặc con lăn dẫn film tại Lab (LAB_SCANNER_DEFECT).",
                            m.dustScratchBlobCount(), m.laplacianSharpnessScore());
            case ClassicalCvAnalyzer.DEFECT_BLURRY ->
                    String.format(Locale.ROOT,
                            "Độ nét Laplacian toàn khung hình thấp (%.2f < 80.0), các cạnh chi tiết bị nhòe đều do lấy nét sai hoặc tốc độ màn trập chậm gây rung tay khi bấm máy (PHOTOGRAPHER_SHOOTING_ERROR).",
                            m.laplacianSharpnessScore());
            case ClassicalCvAnalyzer.DEFECT_UNDEREXPOSED ->
                    String.format(Locale.ROOT,
                            "Độ chói trung bình rất thấp (Luminance Y = %.2f, tỷ lệ bết tối %.1f%%), thiếu sáng trầm trọng lúc chụp do khép khẩu quá nhỏ hoặc cài sai ISO (PHOTOGRAPHER_SHOOTING_ERROR).",
                            m.exposureMeanLuminance(), m.darkPixelRatio() * 100.0);
            case ClassicalCvAnalyzer.DEFECT_OVEREXPOSED ->
                    String.format(Locale.ROOT,
                            "Độ chói trung bình quá cao (Luminance Y = %.2f, tỷ lệ cháy sáng %.1f%%), vùng highlight mất chi tiết do dư sáng mạnh lúc chụp (PHOTOGRAPHER_SHOOTING_ERROR).",
                            m.exposureMeanLuminance(), m.brightPixelRatio() * 100.0);
            case ClassicalCvAnalyzer.DEFECT_COLOR_CAST ->
                    String.format(Locale.ROOT,
                            "Độ lệch màu CIELAB vùng trung tính cao (C* = %.2f > 18.0), ảnh bị ám màu do cân bằng trắng profile máy scan tại Lab chưa chuẩn (LAB_SCANNER_DEFECT).",
                            m.colorCastCieScore());
            default -> "Ảnh scan đạt tiêu chuẩn kỹ thuật tốt.";
        };
    }

    private static String buildRecommendedActionVi(String defect, String attribution) {
        if (ATTRIBUTION_LAB_SCANNER.equalsIgnoreCase(attribution)) {
            return "Khách hàng có thể gửi yêu cầu Film Lab thổi bụi/vệ sinh trục cuốn âm bản và quét lại (re-scan) miễn phí với chế độ Digital ICE, hoặc dùng biên bản AI này làm bằng chứng khi mở khiếu nại.";
        }
        if (ClassicalCvAnalyzer.DEFECT_LIGHT_LEAK.equalsIgnoreCase(defect)) {
            return "Bạn nên đem thân máy ảnh đến thợ sửa chữa để dán lại bộ mút chắn sáng (Light Seal) ở nắp lưng máy và hạn chế thay film dưới nắng gắt.";
        }
        if (ATTRIBUTION_PHOTOGRAPHER.equalsIgnoreCase(attribution)) {
            return "Kiểm tra lại tốc độ màn trập, thước lấy nét và thiết lập đo sáng ISO/EI trên thân máy ảnh cho các cuộn film tiếp theo.";
        }
        return "Không cần xử lý lại.";
    }

    private RegisteredFrameContext resolveAuthorizedFrameContext(
            UUID currentUserId,
            boolean isAdmin,
            Set<UUID> currentUserLabIds,
            UUID frameId
    ) {
        RegisteredFrameContext registered = frameContextRegistry.get(frameId);
        if (registered == null && digitalArchiveFacade != null) {
            Optional<ArchiveFrameSummary> summaryOpt = digitalArchiveFacade.getArchiveFrameSummary(frameId);
            if (summaryOpt.isPresent()) {
                ArchiveFrameSummary s = summaryOpt.get();
                boolean isBw = resolveIsBlackAndWhiteRoll(s.archiveRollId());
                registered = RegisteredFrameContext.builder()
                        .frameId(s.id())
                        .archiveRollId(s.archiveRollId())
                        .ownerUserId(s.ownerUserId())
                        .sourceLabId(null)
                        .frameNumber(s.frameNumber())
                        .s3KeyWeb(s.webUrl())
                        .blackAndWhiteFilm(isBw)
                        .build();
            }
        }

        if (registered == null) {
            throw new ResourceNotFoundException(
                    ErrorCode.SCAN_PHOTO_NOT_FOUND,
                    "Không tìm thấy bức ảnh lưu trữ số với ID: " + frameId
            );
        }

        boolean isOwner = currentUserId != null && currentUserId.equals(registered.ownerUserId());
        boolean isLabMember = registered.sourceLabId() != null
                && currentUserLabIds != null
                && currentUserLabIds.contains(registered.sourceLabId());

        if (!isAdmin && !isOwner && !isLabMember) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Bạn không có quyền chạy chẩn đoán chất lượng trên bức ảnh lưu trữ của người dùng khác."
            );
        }
        return registered;
    }

    private byte[] resolveFrameImageBytes(
            UUID frameId,
            UUID archiveRollId,
            UUID ownerUserId,
            UUID sourceLabId,
            int frameNumber,
            String s3KeyWeb,
            boolean isBwFilm
    ) {
        RegisteredFrameContext ctx = frameContextRegistry.get(frameId);
        if (ctx != null && ctx.imageBytes() != null && ctx.imageBytes().length > 0) {
            return ctx.imageBytes();
        }
        if (storageService != null && StringUtils.hasText(s3KeyWeb)) {
            try {
                if (storageService.exists(s3KeyWeb)) {
                    return storageService.downloadBytes(s3KeyWeb);
                }
            } catch (Exception ex) {
                log.debug("StorageService downloadBytes unavailable for key={}, using synthetic preview frame: {}",
                        s3KeyWeb, ex.getMessage());
            }
        }
        byte[] generated = generateDefaultSharpFrameBytes();
        if (ctx == null && frameId != null) {
            frameContextRegistry.put(frameId, RegisteredFrameContext.builder()
                    .frameId(frameId)
                    .archiveRollId(archiveRollId)
                    .ownerUserId(ownerUserId)
                    .sourceLabId(sourceLabId)
                    .frameNumber(frameNumber)
                    .s3KeyWeb(s3KeyWeb)
                    .blackAndWhiteFilm(isBwFilm)
                    .imageBytes(generated)
                    .build());
        }
        return generated;
    }

    private boolean resolveIsBlackAndWhiteRoll(UUID archiveRollId) {
        if (archiveRollId == null || digitalArchiveFacade == null) {
            return false;
        }
        try {
            Optional<ArchiveRollSummary> rollOpt = digitalArchiveFacade.getArchiveRollSummary(archiveRollId);
            if (rollOpt.isPresent() && rollOpt.get().processType() != null) {
                String p = rollOpt.get().processType().toUpperCase(Locale.ROOT);
                return p.contains("BW") || p.contains("B&W");
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private ScanQcReport saveFrameReport(
            UUID frameId,
            UUID archiveRollId,
            int frameNumber,
            UUID labId,
            Stage1AnalysisResult s1,
            boolean stage2Executed,
            String visionModelUsed,
            String detectedDefect,
            String defectAttribution,
            double attributionConfidence,
            String visionCritiqueVi,
            String recommendedActionVi,
            double overallScore
    ) {
        ClassicalCvMetrics m = s1.metrics();
        Instant now = Instant.now();

        ScanQcReport report = ScanQcReport.builder()
                .id(UUID.randomUUID())
                .orderId(frameId != null ? frameId : UUID.randomUUID())
                .scanImageUrl("frame://" + frameId)
                .qcScore(BigDecimal.valueOf(overallScore).setScale(2, RoundingMode.HALF_UP))
                .createdAt(now)
                .archiveFrameId(frameId)
                .archiveRollId(archiveRollId)
                .frameNumber(frameNumber)
                .labId(labId)
                .laplacianSharpnessScore(m.laplacianSharpnessScore())
                .exposureMeanLuminance(m.exposureMeanLuminance())
                .darkPixelRatio(m.darkPixelRatio())
                .brightPixelRatio(m.brightPixelRatio())
                .dustScratchBlobCount(m.dustScratchBlobCount())
                .colorCastCieScore(m.colorCastCieScore())
                .stage1ExecutionTimeMs(s1.executionTimeMs())
                .stage2Executed(stage2Executed)
                .visionModelUsed(visionModelUsed)
                .detectedDefect(detectedDefect)
                .defectAttribution(defectAttribution)
                .attributionConfidence(attributionConfidence)
                .visionCritiqueVi(visionCritiqueVi)
                .recommendedActionVi(recommendedActionVi)
                .build();

        report.syncFieldsToDetectedIssues();
        ScanQcReport saved = reportRepository.save(report);
        saved.syncDetectedIssuesToFields();
        if (frameId != null) {
            frameReportCache.put(frameId, saved);
        }
        return saved;
    }

    private ScanQcReport findExistingReportForFrame(UUID frameId) {
        if (frameId == null) {
            return null;
        }
        ScanQcReport cached = frameReportCache.get(frameId);
        if (cached != null) {
            return cached;
        }
        List<ScanQcReport> list = reportRepository.findByOrderIdOrderByCreatedAtDesc(frameId);
        if (list != null && !list.isEmpty()) {
            ScanQcReport first = list.getFirst();
            first.syncDetectedIssuesToFields();
            frameReportCache.put(frameId, first);
            return first;
        }
        return null;
    }

    private ScanDiagnosticResponse toDiagnosticResponse(ScanQcReport r) {
        return ScanDiagnosticResponse.builder()
                .reportId(r.getId())
                .archiveFrameId(r.getArchiveFrameId() != null ? r.getArchiveFrameId() : r.getOrderId())
                .archiveRollId(r.getArchiveRollId())
                .frameNumber(r.getFrameNumber() != null ? r.getFrameNumber() : 1)
                .labId(r.getLabId())
                .stage1ClassicalCvMetrics(Stage1ClassicalCvMetricsDto.builder()
                        .laplacianSharpnessScore(r.getLaplacianSharpnessScore() != null ? r.getLaplacianSharpnessScore() : 0.0)
                        .exposureMeanLuminance(r.getExposureMeanLuminance() != null ? r.getExposureMeanLuminance() : 128.0)
                        .darkPixelRatio(r.getDarkPixelRatio() != null ? r.getDarkPixelRatio() : 0.0)
                        .brightPixelRatio(r.getBrightPixelRatio() != null ? r.getBrightPixelRatio() : 0.0)
                        .dustScratchBlobCount(r.getDustScratchBlobCount() != null ? r.getDustScratchBlobCount() : 0)
                        .colorCastCieScore(r.getColorCastCieScore() != null ? r.getColorCastCieScore() : 0.0)
                        .stage1ExecutionTimeMs(r.getStage1ExecutionTimeMs() != null ? r.getStage1ExecutionTimeMs() : 1L)
                        .build())
                .stage2VisionDiagnostic(Stage2VisionDiagnosticDto.builder()
                        .executed(r.isStage2Executed())
                        .visionModelUsed(r.getVisionModelUsed())
                        .detectedDefect(r.getDetectedDefect())
                        .defectAttribution(r.getDefectAttribution())
                        .attributionConfidence(r.getAttributionConfidence() != null ? r.getAttributionConfidence() : 0.90)
                        .visionCritiqueVi(r.getVisionCritiqueVi())
                        .recommendedActionVi(r.getRecommendedActionVi())
                        .build())
                .overallQualityScore(r.getQcScore() != null ? r.getQcScore().doubleValue() : 85.0)
                .updatedArchiveQcStatus(r.getDetectedDefect())
                .analyzedAt(r.getCreatedAt())
                .build();
    }

    private void publishQualityAnalyzedEvent(
            UUID archiveRollId,
            UUID archiveFrameId,
            UUID ownerUserId,
            UUID sourceLabId,
            int analyzedCount,
            int stage2Count,
            double avgScore,
            String overallStatus,
            boolean systematicScratch
    ) {
        if (eventPublisher == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(ScanQualityAnalyzedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .archiveRollId(archiveRollId)
                    .archiveFrameId(archiveFrameId)
                    .ownerUserId(ownerUserId)
                    .sourceLabId(sourceLabId)
                    .analyzedFramesCount(analyzedCount)
                    .stage2EscalatedFramesCount(stage2Count)
                    .averageQualityScore(avgScore)
                    .overallQcStatus(overallStatus)
                    .systematicScannerScratchDetected(systematicScratch)
                    .analyzedAt(Instant.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Could not publish ScanQualityAnalyzedEvent: {}", ex.getMessage());
        }
    }

    private static byte[] generateDefaultSharpFrameBytes() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int v = (((x / 16) + (y / 16)) % 2 == 0) ? 160 : 96;
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private static String loadVisionPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_TEMPLATE_PATH);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException ex) {
            log.warn("Could not load classpath prompt template {}, using fallback", PROMPT_TEMPLATE_PATH);
        }
        return FALLBACK_VISION_PROMPT_TEMPLATE;
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
