package com.analoghub.platform.common.scheduler;

import com.analoghub.platform.common.idempotency.IdempotencyKeyEntity;
import com.analoghub.platform.common.idempotency.IdempotencyKeyRepository;
import com.analoghub.platform.common.idempotency.IdempotencyStatus;
import com.analoghub.platform.common.idempotency.IdempotencyStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CommonSchedulersTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private IdempotencyStoreService idempotencyStoreService;
    private final Map<String, IdempotencyKeyEntity> idempotencyStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        idempotencyStore.clear();
        idempotencyStoreService = new IdempotencyStoreService(idempotencyKeyRepository);

        lenient().when(idempotencyKeyRepository.deleteExpiredKeys(any(Instant.class))).thenAnswer(inv -> {
            Instant cutoff = inv.getArgument(0);
            List<String> keysToDelete = idempotencyStore.values().stream()
                    .filter(k -> k.getExpiresAt() != null && k.getExpiresAt().isBefore(cutoff))
                    .map(IdempotencyKeyEntity::getIdempotencyKey)
                    .toList();
            keysToDelete.forEach(idempotencyStore::remove);
            return keysToDelete.size();
        });
    }

    @Test
    @DisplayName("OutboxRetryScheduler should republish incomplete events stalled for >= 30s and skip fresh, completed, or max-retried events")
    void shouldRepublishOnlyStalledIncompleteEventsWithinMaxRetryLimit() {
        Instant now = Instant.now();

        TestEventPublication stalledIncomplete = new TestEventPublication(
                UUID.fromString("00000000-0000-7000-8000-000000000101"),
                "PaymentSucceededEvent",
                now.minusSeconds(45),
                null,
                EventPublication.Status.FAILED,
                1,
                now.minusSeconds(40)
        );
        TestEventPublication freshIncomplete = new TestEventPublication(
                UUID.fromString("00000000-0000-7000-8000-000000000102"),
                "LabOrderCreatedEvent",
                now.minusSeconds(10),
                null,
                EventPublication.Status.PUBLISHED,
                0,
                null
        );
        TestEventPublication alreadyCompleted = new TestEventPublication(
                UUID.fromString("00000000-0000-7000-8000-000000000103"),
                "UserRegisteredEvent",
                now.minusSeconds(120),
                now.minusSeconds(115),
                EventPublication.Status.COMPLETED,
                1,
                null
        );
        TestEventPublication exhaustedRetries = new TestEventPublication(
                UUID.fromString("00000000-0000-7000-8000-000000000104"),
                "EscrowReleasedEvent",
                now.minusSeconds(300),
                null,
                EventPublication.Status.FAILED,
                5,
                now.minusSeconds(60)
        );

        List<TestEventPublication> outboxTable = List.of(
                stalledIncomplete,
                freshIncomplete,
                alreadyCompleted,
                exhaustedRetries
        );
        List<UUID> resubmittedIds = new ArrayList<>();

        IncompleteEventPublications fakeIncompletePublications = new IncompleteEventPublications() {
            @Override
            public void resubmitIncompletePublications(Predicate<EventPublication> filter) {
                outboxTable.stream()
                        .filter(p -> p.getCompletionDate().isEmpty())
                        .filter(filter)
                        .forEach(p -> resubmittedIds.add(p.getIdentifier()));
            }

            @Override
            public void resubmitIncompletePublicationsOlderThan(Duration duration) {
                Instant cutoff = Instant.now().minus(duration);
                outboxTable.stream()
                        .filter(p -> p.getCompletionDate().isEmpty())
                        .filter(p -> p.getPublicationDate().isBefore(cutoff))
                        .forEach(p -> resubmittedIds.add(p.getIdentifier()));
            }

            @Override
            public void resubmitIncompletePublications(ResubmissionOptions options) {
                Instant cutoff = Instant.now().minus(options.getMinAge());
                outboxTable.stream()
                        .filter(p -> p.getCompletionDate().isEmpty())
                        .filter(p -> p.getPublicationDate().isBefore(cutoff))
                        .filter(options.getFilter())
                        .forEach(p -> resubmittedIds.add(p.getIdentifier()));
            }
        };

        OutboxRetryScheduler scheduler = new OutboxRetryScheduler(fakeIncompletePublications, 30L, 5);
        scheduler.retryIncompleteOutboxEvents();

        assertThat(resubmittedIds)
                .containsExactly(stalledIncomplete.getIdentifier())
                .doesNotContain(
                        freshIncomplete.getIdentifier(),
                        alreadyCompleted.getIdentifier(),
                        exhaustedRetries.getIdentifier()
                );

        // Verify boundary cases: exact 30s cutoff is eligible, null publication or null publicationDate is rejected
        OutboxRetryScheduler defaultScheduler = new OutboxRetryScheduler(fakeIncompletePublications);
        TestEventPublication exactCutoffPublication = new TestEventPublication(
                UUID.randomUUID(),
                "BoundaryEvent",
                now.minusSeconds(30),
                null,
                EventPublication.Status.PUBLISHED,
                0,
                null
        );
        TestEventPublication nullDatePublication = new TestEventPublication(
                UUID.randomUUID(),
                "NullDateEvent",
                null,
                null,
                EventPublication.Status.PUBLISHED,
                0,
                null
        );
        assertThat(defaultScheduler.isEligibleForRetry(exactCutoffPublication, now)).isTrue();
        assertThat(defaultScheduler.isEligibleForRetry(nullDatePublication, now)).isFalse();
        assertThat(defaultScheduler.isEligibleForRetry(null, now)).isFalse();
    }

    @Test
    @DisplayName("IdempotencyAndArchiveCleanupScheduler should delete expired idempotency keys and purge old completed event publications")
    void shouldCleanupExpiredIdempotencyKeysAndCompletedEventPublications() {
        Instant now = Instant.now();

        IdempotencyKeyEntity expiredKey1 = IdempotencyKeyEntity.builder()
                .idempotencyKey("user1:expired-key-1")
                .userId(UUID.randomUUID())
                .requestPath("/api/v1/bookings/orders")
                .requestHash("a".repeat(64))
                .status(IdempotencyStatus.COMPLETED)
                .responseStatus(201)
                .createdAt(now.minus(Duration.ofHours(26)))
                .expiresAt(now.minus(Duration.ofHours(2)))
                .build();
        IdempotencyKeyEntity expiredKey2 = IdempotencyKeyEntity.builder()
                .idempotencyKey("user2:expired-key-2")
                .userId(UUID.randomUUID())
                .requestPath("/api/v1/payments/checkout-url")
                .requestHash("b".repeat(64))
                .status(IdempotencyStatus.COMPLETED)
                .responseStatus(200)
                .createdAt(now.minus(Duration.ofHours(30)))
                .expiresAt(now.minus(Duration.ofHours(6)))
                .build();
        IdempotencyKeyEntity activeKey = IdempotencyKeyEntity.builder()
                .idempotencyKey("user1:active-key")
                .userId(UUID.randomUUID())
                .requestPath("/api/v1/bookings/orders")
                .requestHash("c".repeat(64))
                .status(IdempotencyStatus.COMPLETED)
                .responseStatus(201)
                .createdAt(now.minus(Duration.ofHours(1)))
                .expiresAt(now.plus(Duration.ofHours(23)))
                .build();

        idempotencyStore.put(expiredKey1.getIdempotencyKey(), expiredKey1);
        idempotencyStore.put(expiredKey2.getIdempotencyKey(), expiredKey2);
        idempotencyStore.put(activeKey.getIdempotencyKey(), activeKey);

        List<Duration> purgedRetentionDurations = new ArrayList<>();
        CompletedEventPublications fakeCompletedPublications = new CompletedEventPublications() {
            @Override
            public Collection<? extends EventPublication> findAll() {
                return List.of();
            }

            @Override
            public void deletePublications(Predicate<EventPublication> filter) {
            }

            @Override
            public void deletePublicationsOlderThan(Duration duration) {
                purgedRetentionDurations.add(duration);
            }
        };

        IdempotencyAndArchiveCleanupScheduler cleanupScheduler =
                new IdempotencyAndArchiveCleanupScheduler(idempotencyStoreService, fakeCompletedPublications, 7L);

        int deletedKeys = cleanupScheduler.cleanupExpiredIdempotencyKeysAndArchive();

        assertThat(deletedKeys).isEqualTo(2);
        assertThat(idempotencyStore).containsOnlyKeys("user1:active-key");
        assertThat(purgedRetentionDurations).containsExactly(Duration.ofDays(7));

        // Verify single-arg constructor works safely when CompletedEventPublications is absent
        IdempotencyAndArchiveCleanupScheduler standaloneScheduler =
                new IdempotencyAndArchiveCleanupScheduler(idempotencyStoreService);
        assertThat(standaloneScheduler.cleanupExpiredIdempotencyKeysAndArchive()).isZero();
    }

    private record TestEventPublication(
            UUID identifier,
            Object event,
            Instant publicationDate,
            Instant completionDate,
            Status status,
            int completionAttempts,
            Instant lastResubmissionDate
    ) implements EventPublication {

        @Override
        public UUID getIdentifier() {
            return identifier;
        }

        @Override
        public Object getEvent() {
            return event;
        }

        @Override
        public Instant getPublicationDate() {
            return publicationDate;
        }

        @Override
        public Optional<Instant> getCompletionDate() {
            return Optional.ofNullable(completionDate);
        }

        @Override
        public Status getStatus() {
            return status;
        }

        @Override
        public Instant getLastResubmissionDate() {
            return lastResubmissionDate;
        }

        @Override
        public int getCompletionAttempts() {
            return completionAttempts;
        }
    }
}
