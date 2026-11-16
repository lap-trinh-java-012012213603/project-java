package com.analoghub.platform.aiassistant.service.recommendation;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.LlmPromptRequest;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.client.StructuredLlmResponse;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorRequest;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorRequest.FilmFormatType;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorRequest.LightingCondition;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorRequest.PreferredColorTone;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorResponse;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorResponse.RecommendedFilmStockDto;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intelligent Film Stock Advisor combining Rule-Based Catalog Filtering and Structured LLM Generation
 * (Section 4 of 03-hybrid-recommender-and-semantic-search.md & API 6 of 15-aiassistant.md).
 */
@Slf4j
@Service
public class FilmStockAdvisor {

    public static final String PROMPT_TEMPLATE_PATH = "prompts/advisor/film-stock-advisor-v1.0.st";
    public static final String PROMPT_KEY_FILM_ADVISOR = "film-stock-advisor";
    public static final String PROMPT_VERSION = "advisor-v1.0";
    public static final int TOP_K_RECOMMENDATIONS = 3;

    @Builder
    public record CatalogFilmStockEntry(
            UUID filmStockId,
            String code,
            String brand,
            String name,
            int iso,
            Set<FilmFormatType> supportedFormats,
            String processType,
            String colorBalance,
            String grainLevel,
            BigDecimal typicalPriceVnd,
            Set<PreferredColorTone> bestTones,
            String defaultScannerPairing,
            int compatibleLabsCount,
            int availableMarketplaceListingsCount
    ) {
    }

    @Builder
    public record LlmAdvisorStructuredOutput(
            String advisorSummary,
            List<LlmFilmStockAdviceItem> items
    ) {
    }

    @Builder
    public record LlmFilmStockAdviceItem(
            String code,
            Integer recommendedEiRating,
            String exposureAdvice,
            String preferredScannerPairing
    ) {
    }

    private static final String FALLBACK_PROMPT_TEMPLATE = """
            Bạn là Chuyên gia Tư vấn Cuộn Film & Kỹ thuật Phơi sáng (Film Stock Advisor) của nền tảng AnalogHub tại Việt Nam.
            Bối cảnh: {SHOOTING_SCENARIO} | Ánh sáng: {LIGHTING_CONDITION} | Khổ: {FILM_FORMAT} | Tone: {PREFERRED_COLOR_TONE}
            Ứng viên:
            {CANDIDATE_FILM_STOCKS}
            """;

    private static final String STRUCTURED_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "advisorSummary": { "type": "string" },
                "items": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "code": { "type": "string" },
                      "recommendedEiRating": { "type": "integer" },
                      "exposureAdvice": { "type": "string" },
                      "preferredScannerPairing": { "type": "string" }
                    },
                    "required": ["code", "recommendedEiRating", "exposureAdvice", "preferredScannerPairing"]
                  }
                }
              },
              "required": ["advisorSummary", "items"]
            }
            """;

    private final AiModelRouter aiModelRouter;
    private final AiProperties aiProperties;
    private final String promptTemplate;

    private final List<CatalogFilmStockEntry> catalog = new ArrayList<>();
    private final Map<String, FilmStockAdvisorResponse> responseCache = new ConcurrentHashMap<>();

    @Autowired
    public FilmStockAdvisor(
            AiModelRouter aiModelRouter,
            AiProperties aiProperties
    ) {
        this.aiModelRouter = aiModelRouter;
        this.aiProperties = aiProperties;
        this.promptTemplate = loadPromptTemplate();
        seedCatalog();
    }

    public FilmStockAdvisorResponse adviseFilmStocks(UUID userId, FilmStockAdvisorRequest request) {
        long startNanos = System.nanoTime();
        validateRequest(request);

        String sanitizedScenario = sanitizeAndValidateScenario(request.shootingScenario());

        String cacheKey = buildCacheKey(sanitizedScenario, request);
        FilmStockAdvisorResponse cached = responseCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Step 1: Rule-Based Filter & Domain Ranking on FilmStockCatalog
        List<ScoredFilmCandidate> topCandidates = filterAndRankCandidates(request, TOP_K_RECOMMENDATIONS);

        // Step 2: Structured LLM Generation (with Catalog Grounding Guard)
        String renderedPrompt = renderAdvisorPrompt(sanitizedScenario, request, topCandidates);
        LlmPromptRequest llmRequest = LlmPromptRequest.builder()
                .systemInstruction(renderedPrompt)
                .userPrompt(sanitizedScenario)
                .temperature(0.25)
                .maxOutputTokens(1200)
                .responseSchemaJson(STRUCTURED_SCHEMA_JSON)
                .promptKey(PROMPT_KEY_FILM_ADVISOR)
                .promptVersion(PROMPT_VERSION)
                .build();

        StructuredLlmResponse<LlmAdvisorStructuredOutput> llmResponse = aiModelRouter.generateStructured(
                userId,
                AiModelRouter.FEATURE_FILM_ADVISOR,
                llmRequest,
                LlmAdvisorStructuredOutput.class
        );

        LlmAdvisorStructuredOutput llmData = llmResponse != null ? llmResponse.data() : null;
        Map<String, LlmFilmStockAdviceItem> llmAdviceByCode = new LinkedHashMap<>();
        if (llmData != null && llmData.items() != null) {
            for (LlmFilmStockAdviceItem item : llmData.items()) {
                if (item != null && StringUtils.hasText(item.code())) {
                    llmAdviceByCode.put(item.code().trim().toUpperCase(Locale.ROOT), item);
                }
            }
        }

        List<RecommendedFilmStockDto> recommendedList = new ArrayList<>(topCandidates.size());
        for (int i = 0; i < topCandidates.size(); i++) {
            ScoredFilmCandidate sc = topCandidates.get(i);
            CatalogFilmStockEntry entry = sc.entry();
            LlmFilmStockAdviceItem llmItem = llmAdviceByCode.get(entry.code());

            int recommendedEi = llmItem != null && llmItem.recommendedEiRating() != null && llmItem.recommendedEiRating() > 0
                    ? llmItem.recommendedEiRating()
                    : computeRecommendedEiRating(entry, request);
            String exposureAdvice = llmItem != null && StringUtils.hasText(llmItem.exposureAdvice())
                    ? llmItem.exposureAdvice()
                    : buildDeterministicExposureAdvice(entry, recommendedEi, request);
            String scannerPairing = llmItem != null && StringUtils.hasText(llmItem.preferredScannerPairing())
                    ? llmItem.preferredScannerPairing()
                    : entry.defaultScannerPairing();

            recommendedList.add(RecommendedFilmStockDto.builder()
                    .rank(i + 1)
                    .filmStockId(entry.filmStockId())
                    .code(entry.code())
                    .brand(entry.brand())
                    .name(entry.name())
                    .iso(entry.iso())
                    .filmFormat(request.filmFormat().name())
                    .processType(entry.processType())
                    .colorBalance(entry.colorBalance())
                    .grainLevel(entry.grainLevel())
                    .matchScore(round2(sc.matchScore()))
                    .recommendedEiRating(recommendedEi)
                    .exposureAdvice(exposureAdvice)
                    .preferredScannerPairing(scannerPairing)
                    .compatibleLabsCount(entry.compatibleLabsCount())
                    .availableMarketplaceListingsCount(entry.availableMarketplaceListingsCount())
                    .build());
        }

        String summary = llmData != null && StringUtils.hasText(llmData.advisorSummary())
                ? llmData.advisorSummary()
                : buildDeterministicAdvisorSummary(request, recommendedList);

        ModelProvider providerUsed = llmResponse != null && llmResponse.providerUsed() != null
                ? llmResponse.providerUsed()
                : aiProperties.resolvePrimaryProvider();
        String modelUsed = llmResponse != null && StringUtils.hasText(llmResponse.modelNameUsed())
                ? llmResponse.modelNameUsed()
                : resolveChatModelName(providerUsed);
        long latencyMs = llmResponse != null && llmResponse.latencyMs() > 0
                ? llmResponse.latencyMs()
                : Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);

        FilmStockAdvisorResponse finalResponse = FilmStockAdvisorResponse.builder()
                .advisorSummary(summary)
                .recommendedFilmStocks(List.copyOf(recommendedList))
                .providerUsed(providerUsed.name())
                .modelUsed(modelUsed)
                .latencyMs(latencyMs)
                .build();

        responseCache.put(cacheKey, finalResponse);
        return finalResponse;
    }

    private record ScoredFilmCandidate(
            CatalogFilmStockEntry entry,
            double matchScore
    ) {
    }

    private List<ScoredFilmCandidate> filterAndRankCandidates(FilmStockAdvisorRequest request, int topK) {
        FilmFormatType format = request.filmFormat();
        BigDecimal maxBudget = request.maxBudgetPerRollVnd();
        LightingCondition lighting = request.lightingCondition();
        PreferredColorTone tone = request.preferredColorTone();

        List<CatalogFilmStockEntry> formatFiltered = catalog.stream()
                .filter(e -> e.supportedFormats().contains(format))
                .filter(e -> maxBudget == null || e.typicalPriceVnd().compareTo(maxBudget) <= 0)
                .toList();

        if (formatFiltered.isEmpty()) {
            formatFiltered = catalog.stream()
                    .filter(e -> e.supportedFormats().contains(format))
                    .toList();
        }

        // Section 4 Diagram Rule: For NIGHT_NEON / INDOOR_WARM, filter out low-ISO films (<= 200) if high-ISO exists
        if (lighting == LightingCondition.NIGHT_NEON || lighting == LightingCondition.INDOOR_WARM) {
            List<CatalogFilmStockEntry> highIsoOnly = formatFiltered.stream()
                    .filter(e -> e.iso() >= 400)
                    .toList();
            if (!highIsoOnly.isEmpty()) {
                formatFiltered = highIsoOnly;
            }
        }

        List<ScoredFilmCandidate> scored = new ArrayList<>(formatFiltered.size());
        for (CatalogFilmStockEntry entry : formatFiltered) {
            double score = 0.65;

            // Lighting condition match
            switch (lighting) {
                case NIGHT_NEON -> {
                    if ("TUNGSTEN".equals(entry.colorBalance())) {
                        score += 0.25;
                    } else if (entry.iso() >= 800) {
                        score += 0.20;
                    } else if (entry.iso() >= 400) {
                        score += 0.12;
                    }
                }
                case INDOOR_WARM -> {
                    if (entry.iso() >= 400) {
                        score += 0.18;
                    }
                    if ("TUNGSTEN".equals(entry.colorBalance())) {
                        score += 0.12;
                    }
                }
                case GOLDEN_HOUR -> {
                    if (entry.iso() >= 100 && entry.iso() <= 400 && "DAYLIGHT".equals(entry.colorBalance())) {
                        score += 0.20;
                    }
                }
                case BRIGHT_SUNLIGHT -> {
                    if (entry.iso() <= 200) {
                        score += 0.22;
                    } else if (entry.iso() == 400) {
                        score += 0.14;
                    }
                }
                case OVERCAST_SHADE, MIXED_LIGHTING -> {
                    if (entry.iso() == 400) {
                        score += 0.20;
                    }
                }
            }

            // Preferred color tone match
            if (tone != null && entry.bestTones().contains(tone)) {
                score += 0.12;
            }

            scored.add(new ScoredFilmCandidate(entry, Math.min(0.98, score)));
        }

        scored.sort(Comparator.comparingDouble(ScoredFilmCandidate::matchScore).reversed());
        return scored.subList(0, Math.min(topK, scored.size()));
    }

    private static int computeRecommendedEiRating(CatalogFilmStockEntry entry, FilmStockAdvisorRequest request) {
        LightingCondition lighting = request.lightingCondition();
        if (lighting == LightingCondition.GOLDEN_HOUR && "KODAK_PORTRA_400".equals(entry.code())) {
            return 200; // Overexpose +1 stop for warm pastel skin tones in golden hour
        }
        if (lighting == LightingCondition.BRIGHT_SUNLIGHT && entry.iso() == 400
                && StringUtils.hasText(request.cameraMaxShutterSpeed())
                && (request.cameraMaxShutterSpeed().contains("500") || request.cameraMaxShutterSpeed().contains("1000"))) {
            return 200;
        }
        if (lighting == LightingCondition.NIGHT_NEON && entry.iso() == 400 && "BW".equals(entry.processType())) {
            return 800; // Push +1 stop for B&W night street photography
        }
        return entry.iso();
    }

    private static String buildDeterministicExposureAdvice(
            CatalogFilmStockEntry entry,
            int recommendedEi,
            FilmStockAdvisorRequest request
    ) {
        if (recommendedEi < entry.iso()) {
            return String.format(
                    "Đo sáng ở EI %d (dư sáng +1 stop ưu tiên vùng tối) để đạt tone màu pastel ấm, giữ chi tiết vùng shadow và da mịn màng nhất.",
                    recommendedEi
            );
        }
        if (recommendedEi > entry.iso()) {
            return String.format(
                    "Đo sáng ở EI %d (Push +1 stop) để đảm bảo tốc độ màn trập an toàn khi chụp thiếu sáng ban đêm và báo rõ cho Film Lab khi gửi tráng.",
                    recommendedEi
            );
        }
        if ("TUNGSTEN".equals(entry.colorBalance())) {
            return String.format(
                    "Đo sáng đúng tại EI %d vào vùng sáng trung gian hoặc khuôn mặt dưới ánh đèn neon/đèn đường để tận dụng hiệu ứng cân bằng trắng 3200K.",
                    recommendedEi
            );
        }
        return String.format(
                "Đo sáng chuẩn tại ISO/EI %d; ưu tiên đo sáng vào vùng trung tính để tận dụng tối đa dải tương phản động của film.",
                recommendedEi
        );
    }

    private static String buildDeterministicAdvisorSummary(
            FilmStockAdvisorRequest request,
            List<RecommendedFilmStockDto> recommendations
    ) {
        String topNames = recommendations.stream()
                .map(RecommendedFilmStockDto::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Kodak Portra 400");
        return String.format(
                "Với bối cảnh chụp (%s) trong điều kiện ánh sáng %s trên khổ máy %s, hệ thống khuyến nghị ưu tiên các dòng film: %s.",
                request.shootingScenario(),
                request.lightingCondition().name(),
                request.filmFormat().name(),
                topNames
        );
    }

    private String renderAdvisorPrompt(
            String sanitizedScenario,
            FilmStockAdvisorRequest request,
            List<ScoredFilmCandidate> candidates
    ) {
        StringBuilder candidateText = new StringBuilder();
        for (ScoredFilmCandidate sc : candidates) {
            CatalogFilmStockEntry e = sc.entry();
            candidateText.append(String.format(
                    "- code=%s | name=%s | iso=%d | process=%s | colorBalance=%s | scanner=%s\n",
                    e.code(), e.name(), e.iso(), e.processType(), e.colorBalance(), e.defaultScannerPairing()
            ));
        }

        return promptTemplate
                .replace("{SHOOTING_SCENARIO}", sanitizedScenario)
                .replace("{LIGHTING_CONDITION}", request.lightingCondition().name())
                .replace("{FILM_FORMAT}", request.filmFormat().name())
                .replace("{PREFERRED_COLOR_TONE}", request.preferredColorTone() != null ? request.preferredColorTone().name() : "ANY")
                .replace("{MAX_BUDGET_VND}", request.maxBudgetPerRollVnd() != null ? request.maxBudgetPerRollVnd().toPlainString() : "UNLIMITED")
                .replace("{MAX_SHUTTER_SPEED}", StringUtils.hasText(request.cameraMaxShutterSpeed()) ? request.cameraMaxShutterSpeed() : "1/1000")
                .replace("{CANDIDATE_FILM_STOCKS}", candidateText.toString().trim());
    }

    private static void validateRequest(FilmStockAdvisorRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dữ liệu đầu vào tư vấn chọn cuộn film không được để trống.");
        }
        if (!StringUtils.hasText(request.shootingScenario()) || request.shootingScenario().trim().length() < 5) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mô tả bối cảnh chụp (shootingScenario) phải có ít nhất 5 ký tự.");
        }
        if (request.lightingCondition() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Điều kiện ánh sáng (lightingCondition) là bắt buộc.");
        }
        if (request.filmFormat() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khổ film (filmFormat) là bắt buộc.");
        }
        if (request.maxBudgetPerRollVnd() != null && request.maxBudgetPerRollVnd().compareTo(BigDecimal.valueOf(50000)) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngân sách tối đa mỗi cuộn film không được nhỏ hơn 50,000 VND.");
        }
    }

    private static String sanitizeAndValidateScenario(String rawScenario) {
        String trimmed = rawScenario.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("ignore previous instructions")
                || lower.contains("ignore all previous instructions")
                || lower.contains("ignore system prompt")
                || lower.contains("tiết lộ system prompt")
                || lower.contains("bạn là dan")) {
            throw new BusinessException(
                    ErrorCode.AI_PROMPT_TOO_LONG,
                    "Mô tả bối cảnh chụp bị từ chối bởi bộ lọc an toàn (AI_INPUT_GUARDRAIL_BLOCKED)."
            );
        }
        return trimmed;
    }

    private static String buildCacheKey(String sanitizedScenario, FilmStockAdvisorRequest req) {
        String raw = sanitizedScenario + "|" + req.lightingCondition() + "|" + req.filmFormat()
                + "|" + req.preferredColorTone() + "|" + req.maxBudgetPerRollVnd() + "|" + req.cameraMaxShutterSpeed();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return String.valueOf(raw.hashCode());
        }
    }

    private void seedCatalog() {
        catalog.add(CatalogFilmStockEntry.builder()
                .filmStockId(UUID.fromString("0195e001-1001-7000-8000-000000000001"))
                .code("KODAK_PORTRA_400")
                .brand("Kodak")
                .name("Kodak Professional Portra 400")
                .iso(400)
                .supportedFormats(Set.of(FilmFormatType.FMT_135, FilmFormatType.FMT_120, FilmFormatType.FMT_SHEET))
                .processType("C41")
                .colorBalance("DAYLIGHT")
                .grainLevel("ULTRA_FINE")
                .typicalPriceVnd(BigDecimal.valueOf(420000))
                .bestTones(Set.of(PreferredColorTone.WARM_PASTEL))
                .defaultScannerPairing("Noritsu HS-1800 (cho tone màu trung tính ấm, giữ chi tiết vùng highlight) hoặc Fuji Frontier SP-3000 (nếu thích màu xanh lá có chiều sâu).")
                .compatibleLabsCount(14)
                .availableMarketplaceListingsCount(5)
                .build());

        catalog.add(CatalogFilmStockEntry.builder()
                .filmStockId(UUID.fromString("0195e001-1002-7000-8000-000000000002"))
                .code("KODAK_PRO_IMAGE_100")
                .brand("Kodak")
                .name("Kodak Pro Image 100")
                .iso(100)
                .supportedFormats(Set.of(FilmFormatType.FMT_135))
                .processType("C41")
                .colorBalance("DAYLIGHT")
                .grainLevel("FINE")
                .typicalPriceVnd(BigDecimal.valueOf(260000))
                .bestTones(Set.of(PreferredColorTone.WARM_PASTEL, PreferredColorTone.VIVID_SATURATED))
                .defaultScannerPairing("Fuji Frontier SP-3000 giúp tôn lên sắc vàng ấm của nắng chiều và màu da hồng hào.")
                .compatibleLabsCount(14)
                .availableMarketplaceListingsCount(8)
                .build());

        catalog.add(CatalogFilmStockEntry.builder()
                .filmStockId(UUID.fromString("0195e001-1003-7000-8000-000000000003"))
                .code("CINESTILL_800T")
                .brand("CineStill")
                .name("CineStill 800Tungsten")
                .iso(800)
                .supportedFormats(Set.of(FilmFormatType.FMT_135, FilmFormatType.FMT_120))
                .processType("C41")
                .colorBalance("TUNGSTEN")
                .grainLevel("FINE")
                .typicalPriceVnd(BigDecimal.valueOf(450000))
                .bestTones(Set.of(PreferredColorTone.CINEMATIC_TUNGSTEN))
                .defaultScannerPairing("Noritsu HS-1800 xử lý ánh sáng quầng đỏ (Halation) của CineStill mượt mà hơn, hạn chế vỡ hạt ở vùng tối.")
                .compatibleLabsCount(14)
                .availableMarketplaceListingsCount(6)
                .build());

        catalog.add(CatalogFilmStockEntry.builder()
                .filmStockId(UUID.fromString("0195e001-1004-7000-8000-000000000004"))
                .code("KODAK_VISION3_500T")
                .brand("Kodak")
                .name("Kodak Vision3 500T (5219)")
                .iso(500)
                .supportedFormats(Set.of(FilmFormatType.FMT_135))
                .processType("ECN2")
                .colorBalance("TUNGSTEN")
                .grainLevel("FINE")
                .typicalPriceVnd(BigDecimal.valueOf(220000))
                .bestTones(Set.of(PreferredColorTone.CINEMATIC_TUNGSTEN))
                .defaultScannerPairing("Noritsu HS-1800 (quét phẳng log-profile giữ trọn dải sáng điện ảnh 14 stops của phôi Vision3).")
                .compatibleLabsCount(9)
                .availableMarketplaceListingsCount(12)
                .build());

        catalog.add(CatalogFilmStockEntry.builder()
                .filmStockId(UUID.fromString("0195e001-1005-7000-8000-000000000005"))
                .code("KODAK_PORTRA_800")
                .brand("Kodak")
                .name("Kodak Professional Portra 800")
                .iso(800)
                .supportedFormats(Set.of(FilmFormatType.FMT_135, FilmFormatType.FMT_120))
                .processType("C41")
                .colorBalance("DAYLIGHT")
                .grainLevel("FINE")
                .typicalPriceVnd(BigDecimal.valueOf(490000))
                .bestTones(Set.of(PreferredColorTone.WARM_PASTEL, PreferredColorTone.VIVID_SATURATED))
                .defaultScannerPairing("Noritsu HS-1800 hoặc Fuji Frontier SP-3000 đều cho độ bão hòa màu xuất sắc trong điều kiện thiếu sáng.")
                .compatibleLabsCount(14)
                .availableMarketplaceListingsCount(4)
                .build());

        catalog.add(CatalogFilmStockEntry.builder()
                .filmStockId(UUID.fromString("0195e001-1006-7000-8000-000000000006"))
                .code("FUJIFILM_PRO_400H")
                .brand("Fujifilm")
                .name("Fujifilm Pro 400H")
                .iso(400)
                .supportedFormats(Set.of(FilmFormatType.FMT_135, FilmFormatType.FMT_120))
                .processType("C41")
                .colorBalance("DAYLIGHT")
                .grainLevel("ULTRA_FINE")
                .typicalPriceVnd(BigDecimal.valueOf(480000))
                .bestTones(Set.of(PreferredColorTone.VINTAGE_GREEN, PreferredColorTone.WARM_PASTEL))
                .defaultScannerPairing("Fuji Frontier SP-3000 (sinh ra để tái tạo chuẩn xác lớp màu thứ 4 Cyan-Green pastel đặc trưng của Fujifilm).")
                .compatibleLabsCount(14)
                .availableMarketplaceListingsCount(3)
                .build());

        catalog.add(CatalogFilmStockEntry.builder()
                .filmStockId(UUID.fromString("0195e001-1007-7000-8000-000000000007"))
                .code("KODAK_TRI_X_400")
                .brand("Kodak")
                .name("Kodak Professional Tri-X 400 (400TX)")
                .iso(400)
                .supportedFormats(Set.of(FilmFormatType.FMT_135, FilmFormatType.FMT_120, FilmFormatType.FMT_SHEET))
                .processType("BW")
                .colorBalance("MONOCHROME")
                .grainLevel("CLASSIC_CUBIC")
                .typicalPriceVnd(BigDecimal.valueOf(310000))
                .bestTones(Set.of(PreferredColorTone.HIGH_CONTRAST_BW))
                .defaultScannerPairing("Fuji Frontier SP-3000 (tôn độ tương phản đen trắng mạnh mẽ) hoặc Noritsu HS-1800 (giữ chi tiết vùng shadow khi push EI 1600).")
                .compatibleLabsCount(12)
                .availableMarketplaceListingsCount(9)
                .build());

        catalog.add(CatalogFilmStockEntry.builder()
                .filmStockId(UUID.fromString("0195e001-1008-7000-8000-000000000008"))
                .code("ILFORD_DELTA_400")
                .brand("Ilford")
                .name("Ilford Delta 400 Professional")
                .iso(400)
                .supportedFormats(Set.of(FilmFormatType.FMT_135, FilmFormatType.FMT_120))
                .processType("BW")
                .colorBalance("MONOCHROME")
                .grainLevel("FINE_TABULAR")
                .typicalPriceVnd(BigDecimal.valueOf(295000))
                .bestTones(Set.of(PreferredColorTone.FINE_GRAIN_BW))
                .defaultScannerPairing("Noritsu HS-1800 (giữ dải chuyển xám mượt của tinh thể bạc phẳng Core-Shell).")
                .compatibleLabsCount(12)
                .availableMarketplaceListingsCount(7)
                .build());
    }

    private static String loadPromptTemplate() {
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
        return FALLBACK_PROMPT_TEMPLATE;
    }

    private String resolveChatModelName(ModelProvider provider) {
        if (provider == ModelProvider.OPENAI) {
            String m = aiProperties.getOpenai().getChatModel();
            return StringUtils.hasText(m) ? m : "gpt-4o-mini";
        }
        String m = aiProperties.getGemini().getChatModel();
        return StringUtils.hasText(m) ? m : "gemini-2.5-flash";
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
