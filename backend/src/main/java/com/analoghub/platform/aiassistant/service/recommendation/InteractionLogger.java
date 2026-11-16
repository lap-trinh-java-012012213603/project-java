package com.analoghub.platform.aiassistant.service.recommendation;

import com.analoghub.platform.aiassistant.domain.UserLabInteraction;
import com.analoghub.platform.aiassistant.repository.UserLabInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implicit Feedback Interaction Logger & Time-Decay Affinity Calculator
 * (Section 1.1 & 1.2 of 03-hybrid-recommender-and-semantic-search.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionLogger {

    public static final String TYPE_VIEW_DETAIL = "VIEW_DETAIL";
    public static final String TYPE_BOOKMARK = "BOOKMARK";
    public static final String TYPE_START_BOOKING = "START_BOOKING";
    public static final String TYPE_COMPLETE_ORDER = "COMPLETE_ORDER";
    public static final String TYPE_HIGH_REVIEW = "HIGH_REVIEW";
    public static final String TYPE_LOW_REVIEW = "LOW_REVIEW";

    public static final double WEIGHT_VIEW_DETAIL = 1.0;
    public static final double WEIGHT_BOOKMARK = 3.0;
    public static final double WEIGHT_START_BOOKING = 4.0;
    public static final double WEIGHT_COMPLETE_ORDER = 6.0;
    public static final double WEIGHT_HIGH_REVIEW = 8.0;
    public static final double WEIGHT_LOW_REVIEW = -5.0;

    // Time decay parameter lambda = 0.03 (~23-day half-life)
    public static final double TIME_DECAY_LAMBDA = 0.03;
    public static final long VIEW_DEBOUNCE_SECONDS = 300L;
    public static final double SECONDS_PER_DAY = 86400.0;

    private final UserLabInteractionRepository interactionRepository;

    // Debounce tracker for VIEW_DETAIL (1 view per 5 minutes per user-lab pair)
    private final Map<String, Instant> viewDebounceMap = new ConcurrentHashMap<>();

    @Transactional
    public Optional<UserLabInteraction> logViewDetail(UUID userId, UUID labId) {
        return logViewDetail(userId, labId, Instant.now());
    }

    @Transactional
    public Optional<UserLabInteraction> logViewDetail(UUID userId, UUID labId, Instant occurredAt) {
        if (userId == null || labId == null) {
            return Optional.empty();
        }
        Instant now = occurredAt != null ? occurredAt : Instant.now();
        String debounceKey = userId + ":" + labId;
        Instant lastView = viewDebounceMap.get(debounceKey);
        if (lastView != null && Duration.between(lastView, now).getSeconds() < VIEW_DEBOUNCE_SECONDS) {
            log.debug("Debounced VIEW_DETAIL interaction for userId={}, labId={}", userId, labId);
            return Optional.empty();
        }
        viewDebounceMap.put(debounceKey, now);
        return Optional.of(saveInteraction(userId, labId, TYPE_VIEW_DETAIL, WEIGHT_VIEW_DETAIL, now));
    }

    @Transactional
    public UserLabInteraction logBookmark(UUID userId, UUID labId) {
        return saveInteraction(userId, labId, TYPE_BOOKMARK, WEIGHT_BOOKMARK, Instant.now());
    }

    @Transactional
    public UserLabInteraction logStartBooking(UUID userId, UUID labId) {
        return saveInteraction(userId, labId, TYPE_START_BOOKING, WEIGHT_START_BOOKING, Instant.now());
    }

    @Transactional
    public UserLabInteraction logCompleteOrder(UUID userId, UUID labId) {
        return saveInteraction(userId, labId, TYPE_COMPLETE_ORDER, WEIGHT_COMPLETE_ORDER, Instant.now());
    }

    @Transactional
    public Optional<UserLabInteraction> logReview(UUID userId, UUID labId, int starRating) {
        if (userId == null || labId == null) {
            return Optional.empty();
        }
        if (starRating >= 4) {
            return Optional.of(saveInteraction(userId, labId, TYPE_HIGH_REVIEW, WEIGHT_HIGH_REVIEW, Instant.now()));
        }
        if (starRating <= 2) {
            return Optional.of(saveInteraction(userId, labId, TYPE_LOW_REVIEW, WEIGHT_LOW_REVIEW, Instant.now()));
        }
        return Optional.empty();
    }

    @Transactional
    public UserLabInteraction saveInteraction(
            UUID userId,
            UUID labId,
            String interactionType,
            double baseWeight,
            Instant createdAt
    ) {
        BigDecimal scoreBd = BigDecimal.valueOf(baseWeight).setScale(2, RoundingMode.HALF_UP);
        UserLabInteraction entity = UserLabInteraction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .labId(labId)
                .interactionType(interactionType)
                .interactionScore(scoreBd)
                .createdAt(createdAt != null ? createdAt : Instant.now())
                .build();
        return interactionRepository.save(entity);
    }

    /**
     * Computes raw time-decayed affinity r(u, l) (Section 1.2):
     * r(u, l) = max(0, sum_{i=1}^{N} w_i * exp(-lambda * delta_t_days))
     */
    public double computeRawTimeDecayedAffinity(List<UserLabInteraction> interactions, Instant referenceTime) {
        if (interactions == null || interactions.isEmpty()) {
            return 0.0;
        }
        Instant now = referenceTime != null ? referenceTime : Instant.now();
        double sum = 0.0;
        for (UserLabInteraction interaction : interactions) {
            if (interaction == null) {
                continue;
            }
            double w = interaction.getInteractionScore() != null
                    ? interaction.getInteractionScore().doubleValue()
                    : resolveDefaultWeight(interaction.getInteractionType());
            Instant t = interaction.getCreatedAt() != null ? interaction.getCreatedAt() : now;
            double deltaDays = Math.max(0.0, (now.getEpochSecond() - t.getEpochSecond()) / SECONDS_PER_DAY);
            sum += w * Math.exp(-TIME_DECAY_LAMBDA * deltaDays);
        }
        return Math.max(0.0, sum);
    }

    /**
     * Soft Sigmoid normalization tilde{r}(u, l) in [0, 1] (Section 1.2):
     * tilde{r}(u, l) = 1 / (1 + exp(-(r(u, l) - 5) / 3))
     */
    public static double normalizeAffinitySigmoid(double rawAffinity) {
        if (rawAffinity <= 0.0) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-(rawAffinity - 5.0) / 3.0));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Double> computeUserNormalizedAffinities(UUID userId, Instant referenceTime) {
        if (userId == null) {
            return Map.of();
        }
        List<UserLabInteraction> userInteractions = interactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (userInteractions == null || userInteractions.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UserLabInteraction>> byLab = new LinkedHashMap<>();
        for (UserLabInteraction item : userInteractions) {
            if (item != null && item.getLabId() != null) {
                byLab.computeIfAbsent(item.getLabId(), k -> new java.util.ArrayList<>()).add(item);
            }
        }
        Map<UUID, Double> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<UserLabInteraction>> entry : byLab.entrySet()) {
            double raw = computeRawTimeDecayedAffinity(entry.getValue(), referenceTime);
            if (raw > 0.0) {
                result.put(entry.getKey(), normalizeAffinitySigmoid(raw));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public long countUserInteractions(UUID userId) {
        if (userId == null) {
            return 0L;
        }
        return interactionRepository.countByUserId(userId);
    }

    public static double resolveDefaultWeight(String interactionType) {
        if (interactionType == null) {
            return WEIGHT_VIEW_DETAIL;
        }
        return switch (interactionType.toUpperCase()) {
            case TYPE_BOOKMARK -> WEIGHT_BOOKMARK;
            case TYPE_START_BOOKING -> WEIGHT_START_BOOKING;
            case TYPE_COMPLETE_ORDER -> WEIGHT_COMPLETE_ORDER;
            case TYPE_HIGH_REVIEW -> WEIGHT_HIGH_REVIEW;
            case TYPE_LOW_REVIEW -> WEIGHT_LOW_REVIEW;
            default -> WEIGHT_VIEW_DETAIL;
        };
    }
}
