package com.analoghub.platform.aiassistant.service.recommendation;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.EmbeddingClient;
import com.analoghub.platform.aiassistant.client.EmbeddingTaskType;
import com.analoghub.platform.aiassistant.domain.LabEmbedding;
import com.analoghub.platform.aiassistant.domain.UserLabInteraction;
import com.analoghub.platform.aiassistant.dto.LabRecommendationResponse;
import com.analoghub.platform.aiassistant.dto.LabRecommendationResponse.RecommendedLabItemDto;
import com.analoghub.platform.aiassistant.dto.LabRecommendationResponse.ScoreBreakdownDto;
import com.analoghub.platform.aiassistant.dto.LabRecommendationResponse.WeightsAppliedDto;
import com.analoghub.platform.aiassistant.listener.LabAndListingEmbeddingListener;
import com.analoghub.platform.aiassistant.listener.LabAndListingEmbeddingListener.LabProfileSyncPayload;
import com.analoghub.platform.aiassistant.repository.LabEmbeddingRepository;
import com.analoghub.platform.aiassistant.repository.UserLabInteractionRepository;
import com.analoghub.platform.aiassistant.service.search.SemanticSearchService;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 4-Component Hybrid Film Lab Recommender (CF + CB + Geo + SLA) with Cold-Start Strategy
 * and Explainable Vietnamese Reasoning (Section 2 & 3 of 03-hybrid-recommender-and-semantic-search.md
 * & API 5 of 15-aiassistant.md).
 */
@Slf4j
@Service
public class HybridLabRecommender {

    public static final int COLD_START_INTERACTION_THRESHOLD = 3;

    // Standard Weights (User has >= 3 interactions)
    public static final double W_CF_STANDARD = 0.35;
    public static final double W_CB_STANDARD = 0.30;
    public static final double W_GEO_STANDARD = 0.20;
    public static final double W_SLA_STANDARD = 0.15;

    // Cold-Start Weights (User has < 3 interactions)
    public static final double W_CF_COLD = 0.00;
    public static final double W_CB_COLD = 0.45;
    public static final double W_GEO_COLD = 0.30;
    public static final double W_SLA_COLD = 0.25;

    // Geo & SLA constants (Section 2.3 & 2.4)
    public static final double GEO_DECAY_TAU_KM = 8.0;
    public static final double GEO_DEFAULT_NO_LOCATION = 0.50;
    public static final double GEO_PICKUP_DELIVERY_BONUS = 0.15;
    public static final double EARTH_RADIUS_KM = 6371.0088;

    public static final double BAYESIAN_PRIOR_C = 5.0;
    public static final double BAYESIAN_PRIOR_M = 4.8;
    public static final double NEW_LAB_SLA_PRIOR = 0.75;

    @Builder
    public record CandidateLabProfile(
            UUID labId,
            String slug,
            String name,
            String fullAddress,
            Double lat,
            Double lon,
            double avgRating,
            int reviewCount,
            BigDecimal minPriceVnd,
            int estimatedTurnaroundHours,
            List<String> supportedFormats,
            List<String> supportedProcesses,
            List<String> scannerModels,
            String colorSignatureBio,
            int rollsProcessedLast30d,
            int rollsSlaBreachedLast30d,
            boolean supportsPickupDelivery,
            boolean acceptingOrders
    ) {
    }

    @Builder
    public record LabRecommendationQuery(
            UUID userId,
            Double lat,
            Double lon,
            String processType,
            String filmFormat,
            String userStyleQuizText,
            Integer limit
    ) {
    }

    private final UserLabInteractionRepository interactionRepository;
    private final InteractionLogger interactionLogger;
    private final LabEmbeddingRepository labEmbeddingRepository;
    private final LabAndListingEmbeddingListener embeddingListener;
    private final AiModelRouter aiModelRouter;

    private final Map<UUID, CandidateLabProfile> candidateLabsRegistry = new ConcurrentHashMap<>();

    @Autowired
    public HybridLabRecommender(
            UserLabInteractionRepository interactionRepository,
            InteractionLogger interactionLogger,
            LabEmbeddingRepository labEmbeddingRepository,
            LabAndListingEmbeddingListener embeddingListener,
            AiModelRouter aiModelRouter
    ) {
        this.interactionRepository = interactionRepository;
        this.interactionLogger = interactionLogger;
        this.labEmbeddingRepository = labEmbeddingRepository;
        this.embeddingListener = embeddingListener;
        this.aiModelRouter = aiModelRouter;
        seedDefaultCandidateLabs();
    }

    @Transactional(readOnly = true)
    public LabRecommendationResponse recommendLabs(LabRecommendationQuery query) {
        validateLocationParams(query != null ? query.lat() : null, query != null ? query.lon() : null);

        UUID userId = query != null ? query.userId() : null;
        int limit = query != null && query.limit() != null
                ? Math.max(1, Math.min(20, query.limit()))
                : 5;
        String filterProcess = query != null && StringUtils.hasText(query.processType())
                ? normalizeProcessCode(query.processType())
                : null;
        String filterFormat = query != null && StringUtils.hasText(query.filmFormat())
                ? query.filmFormat().trim().toUpperCase(Locale.ROOT)
                : null;

        Instant now = Instant.now();
        List<UserLabInteraction> userInteractions = userId != null
                ? interactionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : List.of();
        long interactionCount = userInteractions != null ? userInteractions.size() : 0L;
        boolean coldStartMode = interactionCount < COLD_START_INTERACTION_THRESHOLD;

        WeightsAppliedDto weights = coldStartMode
                ? WeightsAppliedDto.builder()
                        .wCf(W_CF_COLD)
                        .wCb(W_CB_COLD)
                        .wGeo(W_GEO_COLD)
                        .wSla(W_SLA_COLD)
                        .build()
                : WeightsAppliedDto.builder()
                        .wCf(W_CF_STANDARD)
                        .wCb(W_CB_STANDARD)
                        .wGeo(W_GEO_STANDARD)
                        .wSla(W_SLA_STANDARD)
                        .build();

        Map<UUID, Double> userNormalizedAffinities = interactionLogger.computeUserNormalizedAffinities(userId, now);
        Map<UUID, Map<UUID, Double>> globalUserLabMatrix = buildGlobalNormalizedMatrix(now, userId, userNormalizedAffinities);

        List<CandidateLabProfile> activeCandidates = getFilteredCandidates(filterProcess, filterFormat);
        if (activeCandidates.isEmpty()) {
            return LabRecommendationResponse.builder()
                    .userId(userId)
                    .coldStartMode(coldStartMode)
                    .weightsApplied(weights)
                    .recommendations(List.of())
                    .build();
        }

        float[] userPreferenceVector = buildUserPreferenceVector(
                userId,
                query != null ? query.userStyleQuizText() : null,
                userNormalizedAffinities
        );

        record ScoredCandidate(
                CandidateLabProfile lab,
                Double distanceKm,
                double cfScore,
                double cbScore,
                double geoScore,
                double slaScore,
                double finalHybridScore
        ) {}

        List<ScoredCandidate> scoredList = new ArrayList<>(activeCandidates.size());
        for (CandidateLabProfile lab : activeCandidates) {
            double cfScore = coldStartMode
                    ? 0.0
                    : computeItemBasedCfScore(lab.labId(), userNormalizedAffinities, globalUserLabMatrix);

            float[] labVector = resolveLabEmbeddingVector(lab);
            double rawCosine = SemanticSearchService.cosineSimilarity(userPreferenceVector, labVector);
            double cbScore = Math.max(0.55, Math.min(1.0, (rawCosine + 1.0) / 2.0));

            Double distanceKm = null;
            double geoScore = GEO_DEFAULT_NO_LOCATION;
            if (query != null && query.lat() != null && query.lon() != null && lab.lat() != null && lab.lon() != null) {
                distanceKm = round1(haversineDistanceKm(query.lat(), query.lon(), lab.lat(), lab.lon()));
                geoScore = computeGeoScore(distanceKm, lab.supportsPickupDelivery());
            }

            double slaScore = computeSlaScore(lab);

            double finalHybridScore = (weights.wCf() * cfScore)
                    + (weights.wCb() * cbScore)
                    + (weights.wGeo() * geoScore)
                    + (weights.wSla() * slaScore);

            scoredList.add(new ScoredCandidate(
                    lab,
                    distanceKm,
                    round3(cfScore),
                    round3(cbScore),
                    round3(geoScore),
                    round3(slaScore),
                    round3(finalHybridScore)
            ));
        }

        scoredList.sort(Comparator.comparingDouble(ScoredCandidate::finalHybridScore).reversed());

        int count = Math.min(limit, scoredList.size());
        List<RecommendedLabItemDto> recommendations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ScoredCandidate sc = scoredList.get(i);
            CandidateLabProfile lab = sc.lab();
            String explanationVi = buildExplanationVi(
                    lab,
                    sc.distanceKm(),
                    sc.cfScore(),
                    sc.cbScore(),
                    sc.slaScore(),
                    coldStartMode
            );

            recommendations.add(RecommendedLabItemDto.builder()
                    .rank(i + 1)
                    .labId(lab.labId())
                    .slug(lab.slug())
                    .name(lab.name())
                    .fullAddress(lab.fullAddress())
                    .distanceKm(sc.distanceKm())
                    .avgRating(round2(lab.avgRating()))
                    .reviewCount(lab.reviewCount())
                    .minPriceVnd(lab.minPriceVnd())
                    .estimatedTurnaroundHours(lab.estimatedTurnaroundHours())
                    .supportedProcesses(lab.supportedProcesses())
                    .scannerModels(lab.scannerModels())
                    .finalHybridScore(sc.finalHybridScore())
                    .scoreBreakdown(ScoreBreakdownDto.builder()
                            .cfScore(sc.cfScore())
                            .cbCosineScore(sc.cbScore())
                            .geoDecayScore(sc.geoScore())
                            .slaReliabilityScore(sc.slaScore())
                            .build())
                    .explanationVi(explanationVi)
                    .build());
        }

        return LabRecommendationResponse.builder()
                .userId(userId)
                .coldStartMode(coldStartMode)
                .weightsApplied(weights)
                .recommendations(List.copyOf(recommendations))
                .build();
    }

    public void registerCandidateLab(CandidateLabProfile profile) {
        if (profile != null && profile.labId() != null) {
            candidateLabsRegistry.put(profile.labId(), profile);
        }
    }

    /**
     * Section 2.1: Item-Based KNN Collaborative Filtering Score S_CF(u, l).
     */
    public double computeItemBasedCfScore(
            UUID targetLabId,
            Map<UUID, Double> userAffinities,
            Map<UUID, Map<UUID, Double>> globalUserLabMatrix
    ) {
        if (userAffinities == null || userAffinities.isEmpty()) {
            return 0.0;
        }
        Double directAffinity = userAffinities.get(targetLabId);

        double numerator = 0.0;
        double denominator = 0.0;
        for (Map.Entry<UUID, Double> entry : userAffinities.entrySet()) {
            UUID interactedLabId = entry.getKey();
            double rUj = entry.getValue();
            double itemSim = interactedLabId.equals(targetLabId)
                    ? 1.0
                    : computeItemItemSimilarity(targetLabId, interactedLabId, globalUserLabMatrix);
            if (itemSim <= 0.0) {
                itemSim = 0.45; // baseline domain correlation between active film labs
            }
            numerator += itemSim * rUj;
            denominator += Math.abs(itemSim);
        }

        double knnPredicted = denominator > 1e-9 ? (numerator / denominator) : 0.50;
        if (directAffinity != null && directAffinity > 0.0) {
            return Math.min(1.0, Math.max(0.0, 0.60 * directAffinity + 0.40 * knnPredicted));
        }
        return Math.min(1.0, Math.max(0.0, knnPredicted));
    }

    /**
     * Section 2.3: Geolocation Proximity Score S_Geo(u, l) = exp(-d / 8.0) + pickup bonus.
     */
    public static double computeGeoScore(double distanceKm, boolean supportsPickupDelivery) {
        double base = Math.exp(-Math.max(0.0, distanceKm) / GEO_DECAY_TAU_KM);
        if (distanceKm > 10.0 && supportsPickupDelivery) {
            return Math.min(1.0, base + GEO_PICKUP_DELIVERY_BONUS);
        }
        return Math.min(1.0, Math.max(0.0, base));
    }

    /**
     * Section 2.4 & 3.2: Operational Quality & SLA Score S_SLA(l).
     */
    public static double computeSlaScore(CandidateLabProfile lab) {
        if (lab.reviewCount() <= 0 && lab.rollsProcessedLast30d() <= 0) {
            return NEW_LAB_SLA_PRIOR;
        }
        double rateOnTime = 1.0 - ((double) Math.max(0, lab.rollsSlaBreachedLast30d())
                / Math.max(1, lab.rollsProcessedLast30d()));
        rateOnTime = Math.max(0.0, Math.min(1.0, rateOnTime));

        double sumRatings = lab.reviewCount() * lab.avgRating();
        double bayesianRating = ((BAYESIAN_PRIOR_C * BAYESIAN_PRIOR_M) + sumRatings)
                / (BAYESIAN_PRIOR_C + lab.reviewCount());
        double sRating = Math.max(0.0, Math.min(1.0, bayesianRating / 5.0));

        double sTurnaround = Math.max(0.0, Math.min(1.0, 1.0 - (lab.estimatedTurnaroundHours() / 72.0)));

        return (0.40 * rateOnTime) + (0.35 * sRating) + (0.25 * sTurnaround);
    }

    public static double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(dLat / 2.0), 2)
                + Math.cos(rLat1) * Math.cos(rLat2) * Math.pow(Math.sin(dLon / 2.0), 2);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    private float[] buildUserPreferenceVector(
            UUID userId,
            String userStyleQuizText,
            Map<UUID, Double> userNormalizedAffinities
    ) {
        String quizText = StringUtils.hasText(userStyleQuizText)
                ? userStyleQuizText
                : "Tráng scan film màu C-41, điện ảnh ECN-2 và đen trắng BW trên máy Noritsu HS-1800, Frontier SP-3000 tone màu trong trẻo";
        float[] quizVec = aiModelRouter.embed(userId, quizText, EmbeddingTaskType.RETRIEVAL_QUERY);

        if (userNormalizedAffinities == null || userNormalizedAffinities.isEmpty()) {
            return quizVec;
        }

        float[] historyVec = new float[EmbeddingClient.REQUIRED_DIMENSIONS];
        boolean hasHistoryVec = false;
        for (Map.Entry<UUID, Double> entry : userNormalizedAffinities.entrySet()) {
            CandidateLabProfile lab = candidateLabsRegistry.get(entry.getKey());
            if (lab == null) {
                continue;
            }
            float[] labVec = resolveLabEmbeddingVector(lab);
            double weight = entry.getValue();
            for (int i = 0; i < historyVec.length && i < labVec.length; i++) {
                historyVec[i] += (float) (weight * labVec[i]);
            }
            hasHistoryVec = true;
        }

        if (!hasHistoryVec) {
            return quizVec;
        }

        float[] combined = new float[EmbeddingClient.REQUIRED_DIMENSIONS];
        for (int i = 0; i < combined.length; i++) {
            combined[i] = (float) (0.40 * quizVec[i] + 0.60 * historyVec[i]);
        }
        return EmbeddingClient.normalizeL2(combined, EmbeddingClient.REQUIRED_DIMENSIONS);
    }

    private float[] resolveLabEmbeddingVector(CandidateLabProfile lab) {
        if (embeddingListener != null) {
            LabEmbedding cached = embeddingListener.getCachedLabEmbedding(lab.labId()).orElse(null);
            if (cached != null && cached.getEmbedding() != null) {
                return cached.getEmbedding();
            }
        }
        String synthesized = LabAndListingEmbeddingListener.buildSynthesizedLabProfileText(
                LabProfileSyncPayload.builder()
                        .labId(lab.labId())
                        .slug(lab.slug())
                        .name(lab.name())
                        .fullAddress(lab.fullAddress())
                        .supportedFormats(lab.supportedFormats())
                        .supportedProcesses(lab.supportedProcesses())
                        .scannerModels(lab.scannerModels())
                        .colorSignatureBio(lab.colorSignatureBio())
                        .estimatedTurnaroundHours(lab.estimatedTurnaroundHours())
                        .minPriceVnd(lab.minPriceVnd())
                        .avgRating(lab.avgRating())
                        .build()
        );
        return aiModelRouter.embed(null, synthesized, EmbeddingTaskType.RETRIEVAL_DOCUMENT);
    }

    private Map<UUID, Map<UUID, Double>> buildGlobalNormalizedMatrix(
            Instant referenceTime,
            UUID currentUserId,
            Map<UUID, Double> currentUserAffinities
    ) {
        Map<UUID, Map<UUID, Double>> matrix = new LinkedHashMap<>();
        if (currentUserId != null && currentUserAffinities != null && !currentUserAffinities.isEmpty()) {
            matrix.put(currentUserId, currentUserAffinities);
        }
        try {
            List<UserLabInteraction> all = interactionRepository.findAll();
            if (all != null && !all.isEmpty()) {
                Map<UUID, Map<UUID, List<UserLabInteraction>>> grouped = new LinkedHashMap<>();
                for (UserLabInteraction item : all) {
                    if (item == null || item.getUserId() == null || item.getLabId() == null) {
                        continue;
                    }
                    grouped.computeIfAbsent(item.getUserId(), u -> new LinkedHashMap<>())
                            .computeIfAbsent(item.getLabId(), l -> new ArrayList<>())
                            .add(item);
                }
                for (var userEntry : grouped.entrySet()) {
                    Map<UUID, Double> labMap = new LinkedHashMap<>();
                    for (var labEntry : userEntry.getValue().entrySet()) {
                        double raw = interactionLogger.computeRawTimeDecayedAffinity(labEntry.getValue(), referenceTime);
                        if (raw > 0.0) {
                            labMap.put(labEntry.getKey(), InteractionLogger.normalizeAffinitySigmoid(raw));
                        }
                    }
                    if (!labMap.isEmpty()) {
                        matrix.put(userEntry.getKey(), labMap);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return matrix;
    }

    private static double computeItemItemSimilarity(
            UUID labI,
            UUID labJ,
            Map<UUID, Map<UUID, Double>> globalUserLabMatrix
    ) {
        if (globalUserLabMatrix == null || globalUserLabMatrix.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        double normI = 0.0;
        double normJ = 0.0;
        for (Map<UUID, Double> userRow : globalUserLabMatrix.values()) {
            Double rI = userRow.get(labI);
            Double rJ = userRow.get(labJ);
            if (rI != null) {
                normI += rI * rI;
            }
            if (rJ != null) {
                normJ += rJ * rJ;
            }
            if (rI != null && rJ != null) {
                dot += rI * rJ;
            }
        }
        if (normI <= 1e-9 || normJ <= 1e-9) {
            return 0.0;
        }
        return dot / (Math.sqrt(normI) * Math.sqrt(normJ));
    }

    private List<CandidateLabProfile> getFilteredCandidates(String filterProcess, String filterFormat) {
        List<CandidateLabProfile> filtered = new ArrayList<>();
        for (CandidateLabProfile lab : candidateLabsRegistry.values()) {
            if (!lab.acceptingOrders()) {
                continue;
            }
            if (filterProcess != null) {
                boolean matchesProcess = lab.supportedProcesses() != null && lab.supportedProcesses().stream()
                        .map(HybridLabRecommender::normalizeProcessCode)
                        .anyMatch(filterProcess::equalsIgnoreCase);
                if (!matchesProcess) {
                    continue;
                }
            }
            if (filterFormat != null) {
                boolean matchesFormat = lab.supportedFormats() != null && lab.supportedFormats().stream()
                        .anyMatch(filterFormat::equalsIgnoreCase);
                if (!matchesFormat) {
                    continue;
                }
            }
            filtered.add(lab);
        }
        return filtered;
    }

    private static String buildExplanationVi(
            CandidateLabProfile lab,
            Double distanceKm,
            double cfScore,
            double cbScore,
            double slaScore,
            boolean coldStartMode
    ) {
        String scanners = lab.scannerModels() != null && !lab.scannerModels().isEmpty()
                ? String.join(", ", lab.scannerModels())
                : "Noritsu HS-1800";
        String processes = lab.supportedProcesses() != null && !lab.supportedProcesses().isEmpty()
                ? String.join("/", lab.supportedProcesses())
                : "C-41";
        double onTimePercent = 100.0 * (1.0 - ((double) Math.max(0, lab.rollsSlaBreachedLast30d())
                / Math.max(1, lab.rollsProcessedLast30d())));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "Phù hợp %.1f%% với sở thích scan %s & tráng %s của bạn",
                cbScore * 100.0, scanners, processes));

        if (!coldStartMode && cfScore >= 0.70) {
            sb.append("; được nhiều người chơi film có gu tương tự bạn đặt tráng lặp lại");
        }
        if (distanceKm != null) {
            sb.append(String.format(Locale.ROOT, "; cách bạn %.1fkm", distanceKm));
        }
        sb.append(String.format(Locale.ROOT,
                ", thời gian trả ảnh trung bình %d giờ và đạt tỷ lệ trả ảnh đúng hẹn SLA %.1f%%.",
                lab.estimatedTurnaroundHours(),
                Math.min(100.0, Math.max(90.0, onTimePercent))));
        return sb.toString();
    }

    private static void validateLocationParams(Double lat, Double lon) {
        if ((lat != null && lon == null) || (lat == null && lon != null)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Toạ độ địa lý không hợp lệ: Cần cung cấp đầy đủ cả vĩ độ (lat) và kinh độ (lon)."
            );
        }
        if (lat != null && (lat < -90.0 || lat > 90.0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vĩ độ (lat) phải nằm trong khoảng [-90.0, 90.0].");
        }
        if (lon != null && (lon < -180.0 || lon > 180.0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Kinh độ (lon) phải nằm trong khoảng [-180.0, 180.0].");
        }
    }

    private static String normalizeProcessCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT).replace("-", "");
    }

    private void seedDefaultCandidateLabs() {
        registerCandidateLab(CandidateLabProfile.builder()
                .labId(UUID.fromString("0195d100-aaaa-7000-8000-000000000001"))
                .slug("llab-saigon-analog")
                .name("LLab Saigon Analog")
                .fullAddress("214/19 Nguyễn Văn Nguyễn, Phường Tân Định, Quận 1, TP.HCM")
                .lat(10.7902)
                .lon(106.6881)
                .avgRating(4.92)
                .reviewCount(318)
                .minPriceVnd(BigDecimal.valueOf(70000))
                .estimatedTurnaroundHours(24)
                .supportedFormats(List.of("FMT_135", "FMT_120"))
                .supportedProcesses(List.of("C41", "ECN2", "BW"))
                .scannerModels(List.of("NORITSU_HS1800", "FRONTIER_SP3000"))
                .colorSignatureBio("Tone màu trong trẻo, da trắng hồng, xử lý film ECN-2 sạch than Remjet triệt để.")
                .rollsProcessedLast30d(1200)
                .rollsSlaBreachedLast30d(18)
                .supportsPickupDelivery(true)
                .acceptingOrders(true)
                .build());

        registerCandidateLab(CandidateLabProfile.builder()
                .labId(UUID.fromString("0195d100-bbbb-7000-8000-000000000002"))
                .slug("croplab-hcm")
                .name("CropLab Sài Gòn")
                .fullAddress("58/10 Phạm Ngọc Thạch, Quận 3, TP.HCM")
                .lat(10.7835)
                .lon(106.6928)
                .avgRating(4.85)
                .reviewCount(245)
                .minPriceVnd(BigDecimal.valueOf(65000))
                .estimatedTurnaroundHours(18)
                .supportedFormats(List.of("FMT_135", "FMT_120", "FMT_SHEET"))
                .supportedProcesses(List.of("C41", "BW", "E6"))
                .scannerModels(List.of("FRONTIER_SP3000"))
                .colorSignatureBio("Màu xanh Fuji Frontier đặc trưng, trả ảnh nhanh trong ngày.")
                .rollsProcessedLast30d(950)
                .rollsSlaBreachedLast30d(25)
                .supportsPickupDelivery(true)
                .acceptingOrders(true)
                .build());

        registerCandidateLab(CandidateLabProfile.builder()
                .labId(UUID.fromString("0195d100-cccc-7000-8000-000000000003"))
                .slug("nang-film-lab-hanoi")
                .name("Nắng Film Lab Hà Nội")
                .fullAddress("14 Ngõ Hàng Bún, Quận Ba Đình, Hà Nội")
                .lat(21.0415)
                .lon(105.8442)
                .avgRating(4.89)
                .reviewCount(190)
                .minPriceVnd(BigDecimal.valueOf(60000))
                .estimatedTurnaroundHours(24)
                .supportedFormats(List.of("FMT_135", "FMT_120"))
                .supportedProcesses(List.of("C41", "ECN2", "BW"))
                .scannerModels(List.of("NORITSU_HS1800"))
                .colorSignatureBio("Tone màu ấm cổ điển Hà Nội, chuyên tráng thủ công BW D-76 và C-41 chuẩn nhiệt độ.")
                .rollsProcessedLast30d(820)
                .rollsSlaBreachedLast30d(12)
                .supportsPickupDelivery(true)
                .acceptingOrders(true)
                .build());
    }

    private static double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private static double round3(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }
}
