package com.analoghub.platform.common.scheduler;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduler #7: Scans every 1 minute for incomplete Spring Modulith Transactional Outbox events
 * in {@code event_publication} ({@code completion_date IS NULL}) that have been stalled for longer
 * than {@code app.outbox.stale-threshold-seconds} (default 30s) and have not exceeded
 * {@code app.outbox.max-retry-attempts} (default 5), and republishes them.
 */
@Slf4j
@Component
public class OutboxRetryScheduler {

    private static final long DEFAULT_STALE_THRESHOLD_SECONDS = 30L;
    private static final int DEFAULT_MAX_RETRY_ATTEMPTS = 5;

    private final IncompleteEventPublications incompleteEventPublications;
    private final long staleThresholdSeconds;
    private final int maxRetryAttempts;

    @Autowired
    public OutboxRetryScheduler(
            IncompleteEventPublications incompleteEventPublications,
            @Value("${app.outbox.stale-threshold-seconds:30}") long staleThresholdSeconds,
            @Value("${app.outbox.max-retry-attempts:5}") int maxRetryAttempts
    ) {
        this.incompleteEventPublications = incompleteEventPublications;
        this.staleThresholdSeconds = staleThresholdSeconds > 0 ? staleThresholdSeconds : DEFAULT_STALE_THRESHOLD_SECONDS;
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public OutboxRetryScheduler(IncompleteEventPublications incompleteEventPublications) {
        this(incompleteEventPublications, DEFAULT_STALE_THRESHOLD_SECONDS, DEFAULT_MAX_RETRY_ATTEMPTS);
    }

    @Scheduled(fixedDelayString = "${app.outbox.retry-scan-ms:60000}")
    @SchedulerLock(name = "OutboxRetryScheduler", lockAtMostFor = "PT55S", lockAtLeastFor = "PT5S")
    public void retryIncompleteOutboxEvents() {
        Instant now = Instant.now();
        AtomicInteger retriedCount = new AtomicInteger(0);

        incompleteEventPublications.resubmitIncompletePublications(publication -> {
            boolean eligible = isEligibleForRetry(publication, now);
            if (eligible) {
                retriedCount.incrementAndGet();
            }
            return eligible;
        });

        if (retriedCount.get() > 0) {
            log.info(
                    "OutboxRetryScheduler republished {} stalled incomplete event publication(s) older than {}s",
                    retriedCount.get(),
                    staleThresholdSeconds
            );
        }
    }

    public boolean isEligibleForRetry(EventPublication publication, Instant now) {
        if (publication == null || publication.isCompleted()) {
            return false;
        }

        Instant publicationDate = publication.getPublicationDate();
        Instant staleCutoff = now.minusSeconds(staleThresholdSeconds);
        if (publicationDate == null || publicationDate.isAfter(staleCutoff)) {
            return false;
        }

        if (maxRetryAttempts > 0 && publication.getCompletionAttempts() >= maxRetryAttempts) {
            log.warn(
                    "Outbox event publication id={} reached max retry attempts ({}/{}), skipping automatic retry",
                    publication.getIdentifier(),
                    publication.getCompletionAttempts(),
                    maxRetryAttempts
            );
            return false;
        }

        return true;
    }
}
