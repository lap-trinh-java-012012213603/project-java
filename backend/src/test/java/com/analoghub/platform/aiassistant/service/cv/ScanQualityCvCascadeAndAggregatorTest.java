package com.analoghub.platform.aiassistant.service.cv;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.GeminiModelAdapter;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.client.OpenAiModelAdapter;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.ScanQcReport;
import com.analoghub.platform.aiassistant.domain.entity.AiUsageLog;
import com.analoghub.platform.aiassistant.dto.ScanDiagnosticResponse;
import com.analoghub.platform.aiassistant.dto.TriggerScanDiagnosticRequest;
import com.analoghub.platform.aiassistant.event.ScanQualityAnalyzedEvent;
import com.analoghub.platform.aiassistant.listener.ArchiveScanQualityListener;
import com.analoghub.platform.aiassistant.repository.AiUsageLogRepository;
import com.analoghub.platform.aiassistant.repository.ScanQcReportRepository;
import com.analoghub.platform.aiassistant.service.cv.ScanQualityCvService.RegisteredFrameContext;
import com.analoghub.platform.aiassistant.service.cv.ScanQualityCvService.RollCascadeSummary;
import com.analoghub.platform.aiassistant.service.governance.AiCostEstimator;
import com.analoghub.platform.aiassistant.service.governance.AiUsageLogger;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.digitalarchive.event.ArchiveRollCreatedEvent;
import com.analoghub.platform.digitalarchive.event.ArchiveRollCreatedEvent.FrameRef;
import com.analoghub.platform.digitalarchive.facade.DigitalArchiveFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanQualityCvCascadeAndAggregatorTest {

    private final List<ScanQcReport> reportStore = new ArrayList<>();
    private final Map<UUID, String> updatedArchiveStatuses = new LinkedHashMap<>();
    private final List<ScanQualityAnalyzedEvent> publishedEvents = new ArrayList<>();

    private ScanQualityCvService scanQualityCvService;
    private ArchiveScanQualityListener archiveScanQualityListener;

    @BeforeEach
    void setUp() {
        reportStore.clear();
        updatedArchiveStatuses.clear();
        publishedEvents.clear();

        AiProperties aiProperties = new AiProperties();
        aiProperties.setActiveProvider(ModelProvider.GEMINI);
        aiProperties.setEmbeddingProvider(ModelProvider.GEMINI);
        aiProperties.getGemini().setApiKey("test");
        aiProperties.getOpenai().setApiKey("test");

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectProvider<Client> nullGeminiClientProvider = new ObjectProvider<>() {
            @Override
            public Client getObject(Object... args) {
                return null;
            }

            @Override
            public Client getIfAvailable() {
                return null;
            }

            @Override
            public Client getIfUnique() {
                return null;
            }

            @Override
            public Client getObject() {
                return null;
            }
        };

        GeminiModelAdapter geminiAdapter = new GeminiModelAdapter(nullGeminiClientProvider, aiProperties, objectMapper);
        OpenAiModelAdapter openAiAdapter = new OpenAiModelAdapter(aiProperties, objectMapper);

        AiUsageLogRepository usageLogRepository = createUsageLogRepositoryStub();
        AiCostEstimator costEstimator = new AiCostEstimator();
        AiUsageLogger usageLogger = new AiUsageLogger(usageLogRepository, costEstimator);

        AiModelRouter aiModelRouter = new AiModelRouter(
                aiProperties,
                usageLogger,
                List.of(geminiAdapter, openAiAdapter),
                List.of(geminiAdapter, openAiAdapter),
                List.of(geminiAdapter, openAiAdapter)
        );

        ClassicalCvAnalyzer classicalCvAnalyzer = new ClassicalCvAnalyzer();
        RollAnomalyAggregator rollAnomalyAggregator = new RollAnomalyAggregator();
        ScanQcReportRepository reportRepository = createReportRepositoryStub();
        DigitalArchiveFacade archiveFacade = createDigitalArchiveFacadeStub();
        ApplicationEventPublisher eventPublisher = event -> {
            if (event instanceof ScanQualityAnalyzedEvent sqe) {
                publishedEvents.add(sqe);
            }
        };

        scanQualityCvService = new ScanQualityCvService(
                classicalCvAnalyzer,
                rollAnomalyAggregator,
                aiModelRouter,
                aiProperties,
                reportRepository,
                archiveFacade,
                null,
                eventPublisher
        );
        archiveScanQualityListener = new ArchiveScanQualityListener(scanQualityCvService);
    }

    @Test
    void fullRollOf36FramesIsScoredInUnder4SecondsAndEscalatesAtMost3FramesToStage2() throws IOException {
        UUID archiveRollId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();

        byte[] sharpBytes = createSharpFrameBytes();
        byte[] blurryBytes = createBlurryFrameBytes();
        byte[] darkBytes = createDarkUnderexposedBytes();
        byte[] lightLeakBytes = createLightLeakFrameBytes();

        List<FrameRef> frameRefs = new ArrayList<>(36);
        for (int i = 1; i <= 36; i++) {
            UUID frameId = UUID.randomUUID();
            byte[] bytes = sharpBytes;
            if (i == 5 || i == 12) {
                bytes = blurryBytes;
            } else if (i == 18 || i == 25) {
                bytes = darkBytes;
            } else if (i == 30) {
                bytes = lightLeakBytes;
            }

            scanQualityCvService.registerFrameContext(RegisteredFrameContext.builder()
                    .frameId(frameId)
                    .archiveRollId(archiveRollId)
                    .ownerUserId(ownerId)
                    .sourceLabId(labId)
                    .frameNumber(i)
                    .s3KeyWeb("private/rolls/" + archiveRollId + "/frame_" + i + ".webp")
                    .blackAndWhiteFilm(false)
                    .imageBytes(bytes)
                    .build());

            frameRefs.add(new FrameRef(frameId, i, "s3/web/" + i, "s3/thumb/" + i));
        }

        ArchiveRollCreatedEvent event = new ArchiveRollCreatedEvent(
                archiveRollId,
                UUID.randomUUID(),
                ownerId,
                labId,
                36,
                frameRefs,
                OffsetDateTime.now()
        );

        RollCascadeSummary summary = scanQualityCvService.analyzeRollCascade(event);

        // Roadmap acceptance criterion: 36 frames scored in < 4s (4000ms), at most 3 escalated to Stage 2
        assertThat(summary.totalFramesAnalyzed()).isEqualTo(36);
        assertThat(summary.totalExecutionTimeMs()).isLessThan(4000L);
        assertThat(summary.stage2EscalatedCount()).isEqualTo(3);
        assertThat(updatedArchiveStatuses).hasSize(36);
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.getFirst().analyzedFramesCount()).isEqualTo(36);
        assertThat(publishedEvents.getFirst().stage2EscalatedFramesCount()).isEqualTo(3);
    }

    @Test
    void cleanRollOf36SharpFramesSkipsStage2WithZeroVisionCalls() throws IOException {
        UUID archiveRollId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();
        byte[] sharpBytes = createSharpFrameBytes();

        List<FrameRef> frameRefs = new ArrayList<>(36);
        for (int i = 1; i <= 36; i++) {
            UUID frameId = UUID.randomUUID();
            scanQualityCvService.registerFrameContext(RegisteredFrameContext.builder()
                    .frameId(frameId)
                    .archiveRollId(archiveRollId)
                    .ownerUserId(ownerId)
                    .sourceLabId(labId)
                    .frameNumber(i)
                    .imageBytes(sharpBytes)
                    .build());
            frameRefs.add(new FrameRef(frameId, i, "s3/web/" + i, "s3/thumb/" + i));
        }

        ArchiveRollCreatedEvent event = new ArchiveRollCreatedEvent(
                archiveRollId,
                UUID.randomUUID(),
                ownerId,
                labId,
                36,
                frameRefs,
                OffsetDateTime.now()
        );

        archiveScanQualityListener.onArchiveRollCreated(event);

        assertThat(publishedEvents).hasSize(1);
        ScanQualityAnalyzedEvent published = publishedEvents.getFirst();
        assertThat(published.stage2EscalatedFramesCount()).isZero();
        assertThat(published.overallQcStatus()).isEqualTo(ClassicalCvAnalyzer.DEFECT_GOOD);
    }

    @Test
    void rollAnomalyAggregatorDetectsRecurringScratchAcross5ConsecutiveFrames() throws IOException {
        UUID archiveRollId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();

        byte[] sharpBytes = createSharpFrameBytes();
        byte[] scratchedAtY70Bytes = createFrameWithHorizontalScratchAtY(70);

        List<FrameRef> frameRefs = new ArrayList<>(10);
        for (int i = 1; i <= 10; i++) {
            UUID frameId = UUID.randomUUID();
            // Frames 3..7 (5 consecutive frames) have a scratch at the exact same y=70 coordinate
            byte[] bytes = (i >= 3 && i <= 7) ? scratchedAtY70Bytes : sharpBytes;

            scanQualityCvService.registerFrameContext(RegisteredFrameContext.builder()
                    .frameId(frameId)
                    .archiveRollId(archiveRollId)
                    .ownerUserId(ownerId)
                    .sourceLabId(labId)
                    .frameNumber(i)
                    .imageBytes(bytes)
                    .build());
            frameRefs.add(new FrameRef(frameId, i, "s3/web/" + i, "s3/thumb/" + i));
        }

        RollCascadeSummary summary = scanQualityCvService.analyzeRollCascade(new ArchiveRollCreatedEvent(
                archiveRollId,
                UUID.randomUUID(),
                ownerId,
                labId,
                10,
                frameRefs,
                OffsetDateTime.now()
        ));

        assertThat(summary.systematicScratchDetected()).isTrue();
        assertThat(summary.overallRollStatus()).isEqualTo("SYSTEMATIC_LAB_SCRATCH_DETECTED");
        assertThat(summary.systematicScratchAlertVi())
                .contains("phát hiện vệt xước cố định trên khay scan, kỹ thuật viên vui lòng dừng máy để vệ sinh trục cuốn");
    }

    @Test
    void diagnoseFrameAttributesLabScannerDefectVsPhotographerErrorAndEnforcesAccessControl() throws IOException {
        UUID ownerId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        UUID archiveRollId = UUID.randomUUID();

        // Frame 1: Heavy dust & scanner scratch -> LAB_SCANNER_DEFECT
        UUID dustyFrameId = UUID.randomUUID();
        scanQualityCvService.registerFrameContext(RegisteredFrameContext.builder()
                .frameId(dustyFrameId)
                .archiveRollId(archiveRollId)
                .ownerUserId(ownerId)
                .sourceLabId(labId)
                .frameNumber(18)
                .imageBytes(createHeavyDustAndScratchFrameBytes())
                .build());

        ScanDiagnosticResponse labDefectResponse = scanQualityCvService.diagnoseFrame(
                ownerId,
                false,
                Set.of(),
                dustyFrameId,
                TriggerScanDiagnosticRequest.builder()
                        .runVisionLlmStage2(true)
                        .userSymptomNote("Trên nền ảnh có nhiều chấm bụi trắng và vệt xước dọc")
                        .build()
        );

        assertThat(labDefectResponse.stage2VisionDiagnostic().executed()).isTrue();
        assertThat(labDefectResponse.stage2VisionDiagnostic().detectedDefect())
                .isEqualTo(ClassicalCvAnalyzer.DEFECT_DUST_SCRATCH);
        assertThat(labDefectResponse.stage2VisionDiagnostic().defectAttribution())
                .isEqualTo(ScanQualityCvService.ATTRIBUTION_LAB_SCANNER);
        assertThat(labDefectResponse.stage2VisionDiagnostic().recommendedActionVi()).containsIgnoringCase("Digital ICE");

        // Frame 2: Asymmetric orange border light leak -> PHOTOGRAPHER_SHOOTING_ERROR
        UUID lightLeakFrameId = UUID.randomUUID();
        scanQualityCvService.registerFrameContext(RegisteredFrameContext.builder()
                .frameId(lightLeakFrameId)
                .archiveRollId(archiveRollId)
                .ownerUserId(ownerId)
                .sourceLabId(labId)
                .frameNumber(24)
                .imageBytes(createLightLeakFrameBytes())
                .build());

        ScanDiagnosticResponse photographerErrorResponse = scanQualityCvService.diagnoseFrame(
                UUID.randomUUID(), // Lab technician user
                false,
                Set.of(labId),     // Member of the source Film Lab
                lightLeakFrameId,
                TriggerScanDiagnosticRequest.builder().runVisionLlmStage2(true).build()
        );

        assertThat(photographerErrorResponse.stage2VisionDiagnostic().detectedDefect())
                .isEqualTo(ClassicalCvAnalyzer.DEFECT_LIGHT_LEAK);
        assertThat(photographerErrorResponse.stage2VisionDiagnostic().defectAttribution())
                .isEqualTo(ScanQualityCvService.ATTRIBUTION_PHOTOGRAPHER);

        // Unauthorized stranger must be rejected with 403 ACCESS_DENIED
        assertThatThrownBy(() -> scanQualityCvService.diagnoseFrame(
                strangerId,
                false,
                Set.of(),
                dustyFrameId,
                TriggerScanDiagnosticRequest.builder().build()
        )).isInstanceOf(BusinessException.class);
    }

    private static byte[] createSharpFrameBytes() throws IOException {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int v = (((x / 16) + (y / 16)) % 2 == 0) ? 160 : 96;
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return toPngBytes(img);
    }

    private static byte[] createBlurryFrameBytes() throws IOException {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int v = 115 + (int) (20.0 * ((double) x / 128.0));
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return toPngBytes(img);
    }

    private static byte[] createDarkUnderexposedBytes() throws IOException {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int v = 8;
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return toPngBytes(img);
    }

    private static byte[] createLightLeakFrameBytes() throws IOException {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        int rightEdgeStart = (int) (128 * 0.90);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                if (x >= rightEdgeStart) {
                    img.setRGB(x, y, (255 << 16) | (95 << 8) | 15);
                } else {
                    int v = (((x / 8) + (y / 8)) % 2 == 0) ? 75 : 50;
                    img.setRGB(x, y, (v << 16) | (v << 8) | v);
                }
            }
        }
        return toPngBytes(img);
    }

    private static byte[] createFrameWithHorizontalScratchAtY(int scratchY) throws IOException {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(new Color(120, 120, 120));
        g2d.fillRect(0, 0, 128, 128);
        g2d.setColor(new Color(248, 248, 248));
        g2d.fillRect(15, scratchY, 70, 2);
        g2d.dispose();
        return toPngBytes(img);
    }

    private static byte[] createHeavyDustAndScratchFrameBytes() throws IOException {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int v = (((x / 32) + (y / 32)) % 2 == 0) ? 140 : 105;
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(new Color(250, 250, 250));
        // Add 45 dust specks so dustScratchBlobCount > 40
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 9; c++) {
                int dx = 16 + (c * 24);
                int dy = 20 + (r * 40);
                g2d.fillRect(dx, dy, 2, 2);
            }
        }
        g2d.dispose();
        return toPngBytes(img);
    }

    private static byte[] toPngBytes(BufferedImage img) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        }
    }

    @SuppressWarnings("unchecked")
    private ScanQcReportRepository createReportRepositoryStub() {
        return (ScanQcReportRepository) Proxy.newProxyInstance(
                ScanQcReportRepository.class.getClassLoader(),
                new Class<?>[]{ScanQcReportRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("save".equals(name)) {
                        ScanQcReport r = (ScanQcReport) args[0];
                        if (r.getId() == null) {
                            r.setId(UUID.randomUUID());
                        }
                        reportStore.add(r);
                        return r;
                    }
                    if ("findByOrderIdOrderByCreatedAtDesc".equals(name)) {
                        UUID orderOrFrameId = (UUID) args[0];
                        return reportStore.stream()
                                .filter(r -> orderOrFrameId.equals(r.getOrderId()))
                                .toList();
                    }
                    return null;
                }
        );
    }

    private DigitalArchiveFacade createDigitalArchiveFacadeStub() {
        return new DigitalArchiveFacade() {
            @Override
            public void updateFrameQuality(UUID frameId, String qcStatus, BigDecimal qcOverallScore) {
                updatedArchiveStatuses.put(frameId, qcStatus);
            }

            @Override
            public Optional<ArchiveRollSummary> getArchiveRollSummary(UUID archiveRollId) {
                return Optional.empty();
            }

            @Override
            public Optional<ArchiveFrameSummary> getArchiveFrameSummary(UUID frameId) {
                return Optional.empty();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private AiUsageLogRepository createUsageLogRepositoryStub() {
        return (AiUsageLogRepository) Proxy.newProxyInstance(
                AiUsageLogRepository.class.getClassLoader(),
                new Class<?>[]{AiUsageLogRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        AiUsageLog entry = (AiUsageLog) args[0];
                        if (entry.getId() == null) {
                            entry.setId(UUID.randomUUID());
                        }
                        return entry;
                    }
                    return null;
                }
        );
    }
}
