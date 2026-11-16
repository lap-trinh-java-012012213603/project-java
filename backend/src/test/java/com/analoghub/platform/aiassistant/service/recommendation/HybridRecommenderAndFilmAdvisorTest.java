package com.analoghub.platform.aiassistant.service.recommendation;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.GeminiModelAdapter;
import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.client.OpenAiModelAdapter;
import com.analoghub.platform.aiassistant.config.AiProperties;
import com.analoghub.platform.aiassistant.domain.LabEmbedding;
import com.analoghub.platform.aiassistant.domain.ListingEmbedding;
import com.analoghub.platform.aiassistant.domain.UserLabInteraction;
import com.analoghub.platform.aiassistant.domain.entity.AiUsageLog;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorRequest;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorRequest.FilmFormatType;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorRequest.LightingCondition;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorRequest.PreferredColorTone;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorResponse;
import com.analoghub.platform.aiassistant.dto.LabRecommendationResponse;
import com.analoghub.platform.aiassistant.dto.LabRecommendationResponse.RecommendedLabItemDto;
import com.analoghub.platform.aiassistant.listener.LabAndListingEmbeddingListener;
import com.analoghub.platform.aiassistant.listener.UserLabInteractionListener;
import com.analoghub.platform.aiassistant.repository.AiUsageLogRepository;
import com.analoghub.platform.aiassistant.repository.LabEmbeddingRepository;
import com.analoghub.platform.aiassistant.repository.ListingEmbeddingRepository;
import com.analoghub.platform.aiassistant.repository.UserLabInteractionRepository;
import com.analoghub.platform.aiassistant.service.governance.AiCostEstimator;
import com.analoghub.platform.aiassistant.service.governance.AiUsageLogger;
import com.analoghub.platform.aiassistant.service.recommendation.HybridLabRecommender.LabRecommendationQuery;
import com.analoghub.platform.booking.event.LabOrderCreatedEvent;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.filmlab.event.LabViewedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HybridRecommenderAndFilmAdvisorTest {

    private final List<UserLabInteraction> interactionStore = new ArrayList<>();

    private InteractionLogger interactionLogger;
    private UserLabInteractionListener interactionListener;
    private HybridLabRecommender hybridLabRecommender;
    private FilmStockAdvisor filmStockAdvisor;

    @BeforeEach
    void setUp() {
        interactionStore.clear();

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

        UserLabInteractionRepository interactionRepository = createInteractionRepositoryStub();
        LabEmbeddingRepository labEmbeddingRepository = createLabEmbeddingRepositoryStub();
        ListingEmbeddingRepository listingEmbeddingRepository = createListingEmbeddingRepositoryStub();

        interactionLogger = new InteractionLogger(interactionRepository);
        interactionListener = new UserLabInteractionListener(interactionLogger);

        LabAndListingEmbeddingListener embeddingListener = new LabAndListingEmbeddingListener(
                labEmbeddingRepository,
                listingEmbeddingRepository,
                aiModelRouter
        );

        hybridLabRecommender = new HybridLabRecommender(
                interactionRepository,
                interactionLogger,
                labEmbeddingRepository,
                embeddingListener,
                aiModelRouter
        );

        filmStockAdvisor = new FilmStockAdvisor(
                aiModelRouter,
                aiProperties
        );
    }

    @Test
    void interactionLoggerComputesTimeDecayAndDebouncesRepeatedViews() {
        UUID userId = UUID.randomUUID();
        UUID labId = UUID.fromString("0195d100-aaaa-7000-8000-000000000001");
        Instant now = Instant.now();

        Optional<UserLabInteraction> firstView = interactionLogger.logViewDetail(userId, labId, now);
        Optional<UserLabInteraction> secondViewWithin5Min = interactionLogger.logViewDetail(userId, labId, now.plusSeconds(120));
        assertThat(firstView).isPresent();
        assertThat(secondViewWithin5Min).isEmpty();

        // Event listener tests
        UUID anotherLabId = UUID.fromString("0195d100-bbbb-7000-8000-000000000002");
        interactionListener.onLabViewed(new LabViewedEvent(anotherLabId, userId.toString()));
        interactionListener.onLabOrderCreated(new LabOrderCreatedEvent(
                UUID.randomUUID(),
                "ORD-1001",
                userId,
                labId,
                2,
                BigDecimal.valueOf(140000),
                OffsetDateTime.now().plusMinutes(15)
        ));

        // Time decay check: interaction 23.1 days ago decays by ~50% (exp(-0.03 * 23.1) ~= 0.50)
        UserLabInteraction oldInteraction = UserLabInteraction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .labId(labId)
                .interactionType(InteractionLogger.TYPE_COMPLETE_ORDER)
                .interactionScore(BigDecimal.valueOf(6.00))
                .createdAt(now.minus(23, ChronoUnit.DAYS))
                .build();

        double decayedScore = interactionLogger.computeRawTimeDecayedAffinity(List.of(oldInteraction), now);
        assertThat(decayedScore).isBetween(2.90, 3.10); // ~half of 6.00
    }

    @Test
    void coldStartUserSwitchesWeightsAndReturnsExplainableLabRecommendations() {
        UUID newUserId = UUID.randomUUID();

        // Passing only lat without lon must throw VALIDATION_ERROR
        assertThatThrownBy(() -> hybridLabRecommender.recommendLabs(LabRecommendationQuery.builder()
                .userId(newUserId)
                .lat(10.7769)
                .lon(null)
                .build())).isInstanceOf(BusinessException.class);

        LabRecommendationResponse response = hybridLabRecommender.recommendLabs(LabRecommendationQuery.builder()
                .userId(newUserId)
                .lat(10.7769)
                .lon(106.7009)
                .processType("C41")
                .filmFormat("FMT_135")
                .limit(5)
                .build());

        assertThat(response.coldStartMode()).isTrue();
        assertThat(response.weightsApplied().wCf()).isEqualTo(0.00);
        assertThat(response.weightsApplied().wCb()).isEqualTo(0.45);
        assertThat(response.weightsApplied().wGeo()).isEqualTo(0.30);
        assertThat(response.weightsApplied().wSla()).isEqualTo(0.25);
        assertThat(response.recommendations()).isNotEmpty();

        RecommendedLabItemDto topLab = response.recommendations().getFirst();
        assertThat(topLab.rank()).isEqualTo(1);
        assertThat(topLab.distanceKm()).isNotNull();
        assertThat(topLab.finalHybridScore()).isBetween(0.50, 1.00);
        assertThat(topLab.explanationVi()).isNotBlank();
        assertThat(topLab.explanationVi()).contains("SLA");
    }

    @Test
    void experiencedUserAppliesStandardFourComponentWeights() {
        UUID userId = UUID.randomUUID();
        UUID saigonLabId = UUID.fromString("0195d100-aaaa-7000-8000-000000000001");

        // Log >= 3 interactions so Cold-Start is deactivated
        interactionLogger.logViewDetail(userId, saigonLabId);
        interactionLogger.logStartBooking(userId, saigonLabId);
        interactionLogger.logCompleteOrder(userId, saigonLabId);
        interactionLogger.logReview(userId, saigonLabId, 5);

        LabRecommendationResponse response = hybridLabRecommender.recommendLabs(LabRecommendationQuery.builder()
                .userId(userId)
                .lat(10.7850)
                .lon(106.6900)
                .limit(3)
                .build());

        assertThat(response.coldStartMode()).isFalse();
        assertThat(response.weightsApplied().wCf()).isEqualTo(0.35);
        assertThat(response.weightsApplied().wCb()).isEqualTo(0.30);
        assertThat(response.weightsApplied().wGeo()).isEqualTo(0.20);
        assertThat(response.weightsApplied().wSla()).isEqualTo(0.15);

        RecommendedLabItemDto first = response.recommendations().getFirst();
        assertThat(first.labId()).isEqualTo(saigonLabId);
        assertThat(first.scoreBreakdown().cfScore()).isGreaterThan(0.70);
        assertThat(first.explanationVi()).contains("NORITSU_HS1800").contains("đặt tráng lặp lại");
    }

    @Test
    void filmStockAdvisorRecommendsGroundedCatalogFilmsForNightNeonAndGoldenHour() {
        UUID userId = UUID.randomUUID();

        FilmStockAdvisorResponse nightResponse = filmStockAdvisor.adviseFilmStocks(
                userId,
                FilmStockAdvisorRequest.builder()
                        .shootingScenario("Chụp đường phố ban đêm dưới ánh đèn neon và biển hiệu vàng với ống kính f/2.8")
                        .lightingCondition(LightingCondition.NIGHT_NEON)
                        .filmFormat(FilmFormatType.FMT_135)
                        .preferredColorTone(PreferredColorTone.CINEMATIC_TUNGSTEN)
                        .maxBudgetPerRollVnd(BigDecimal.valueOf(500000))
                        .cameraMaxShutterSpeed("1/500")
                        .build()
        );

        assertThat(nightResponse.recommendedFilmStocks()).hasSize(3);
        assertThat(nightResponse.recommendedFilmStocks().getFirst().code()).isEqualTo("CINESTILL_800T");
        assertThat(nightResponse.recommendedFilmStocks())
                .allMatch(item -> item.iso() >= 400); // Low ISO (<= 200) filtered out for NIGHT_NEON

        FilmStockAdvisorResponse goldenHourResponse = filmStockAdvisor.adviseFilmStocks(
                userId,
                FilmStockAdvisorRequest.builder()
                        .shootingScenario("Chụp chân dung áo dài ngoại cảnh ở Huế vào buổi chiều nắng nhẹ xen lẫn bóng râm")
                        .lightingCondition(LightingCondition.GOLDEN_HOUR)
                        .filmFormat(FilmFormatType.FMT_135)
                        .preferredColorTone(PreferredColorTone.WARM_PASTEL)
                        .maxBudgetPerRollVnd(BigDecimal.valueOf(450000))
                        .cameraMaxShutterSpeed("1/1000")
                        .build()
        );

        assertThat(goldenHourResponse.recommendedFilmStocks()).isNotEmpty();
        assertThat(goldenHourResponse.recommendedFilmStocks().getFirst().code()).isEqualTo("KODAK_PORTRA_400");
        assertThat(goldenHourResponse.recommendedFilmStocks().getFirst().recommendedEiRating()).isEqualTo(200);
        assertThat(goldenHourResponse.recommendedFilmStocks().getFirst().preferredScannerPairing()).contains("Noritsu");
    }

    @SuppressWarnings("unchecked")
    private UserLabInteractionRepository createInteractionRepositoryStub() {
        return (UserLabInteractionRepository) Proxy.newProxyInstance(
                UserLabInteractionRepository.class.getClassLoader(),
                new Class<?>[]{UserLabInteractionRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("save".equals(name)) {
                        UserLabInteraction e = (UserLabInteraction) args[0];
                        if (e.getId() == null) {
                            e.setId(UUID.randomUUID());
                        }
                        interactionStore.add(e);
                        return e;
                    }
                    if ("findByUserIdOrderByCreatedAtDesc".equals(name)) {
                        UUID userId = (UUID) args[0];
                        return interactionStore.stream()
                                .filter(i -> userId.equals(i.getUserId()))
                                .sorted(Comparator.comparing(UserLabInteraction::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                                .toList();
                    }
                    if ("countByUserId".equals(name)) {
                        UUID userId = (UUID) args[0];
                        return interactionStore.stream()
                                .filter(i -> userId.equals(i.getUserId()))
                                .count();
                    }
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(interactionStore);
                    }
                    return null;
                }
        );
    }

    @SuppressWarnings("unchecked")
    private LabEmbeddingRepository createLabEmbeddingRepositoryStub() {
        return (LabEmbeddingRepository) Proxy.newProxyInstance(
                LabEmbeddingRepository.class.getClassLoader(),
                new Class<?>[]{LabEmbeddingRepository.class},
                (proxy, method, args) -> {
                    if ("findAll".equals(method.getName())) {
                        return List.<LabEmbedding>of();
                    }
                    return Optional.empty();
                }
        );
    }

    @SuppressWarnings("unchecked")
    private ListingEmbeddingRepository createListingEmbeddingRepositoryStub() {
        return (ListingEmbeddingRepository) Proxy.newProxyInstance(
                ListingEmbeddingRepository.class.getClassLoader(),
                new Class<?>[]{ListingEmbeddingRepository.class},
                (proxy, method, args) -> {
                    if ("findAll".equals(method.getName())) {
                        return List.<ListingEmbedding>of();
                    }
                    return Optional.empty();
                }
        );
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
