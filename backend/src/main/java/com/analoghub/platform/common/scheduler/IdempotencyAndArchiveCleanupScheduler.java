package com.analoghub.platform.common.scheduler;

import com.analoghub.platform.common.idempotency.IdempotencyStoreService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Scheduler #8: Runs daily at 02:00 AM (with distributed {@code ShedLock}) to purge expired
 * idempotency keys ({@code sys_idempotency_keys.expires_at < now()}) and clean up old completed
 * outbox publications.
 */
@Slf4j
@Component
public class IdempotencyAndArchiveCleanupScheduler {

    private static final long DEFAULT_COMPLETED_EVENT_RETENTION_DAYS = 7L;

    private final IdempotencyStoreService idempotencyStoreService;
    private final CompletedEventPublications completedEventPublications;
    private final long completedEventRetentionDays;

    @Autowired
    public IdempotencyAndArchiveCleanupScheduler(
            IdempotencyStoreService idempotencyStoreService,
            ObjectProvider<CompletedEventPublications> completedEventPublicationsProvider,
            @Value("${app.outbox.completed-retention-days:7}") long completedEventRetentionDays
    ) {
        this(
                idempotencyStoreService,
                completedEventPublicationsProvider != null ? completedEventPublicationsProvider.getIfAvailable() : null,
                completedEventRetentionDays
        );
    }

    public IdempotencyAndArchiveCleanupScheduler(
            IdempotencyStoreService idempotencyStoreService,
            CompletedEventPublications completedEventPublications,
            long completedEventRetentionDays
    ) {
        this.idempotencyStoreService = idempotencyStoreService;
        this.completedEventPublications = completedEventPublications;
        this.completedEventRetentionDays = completedEventRetentionDays > 0
                ? completedEventRetentionDays
                : DEFAULT_COMPLETED_EVENT_RETENTION_DAYS;
    }

    public IdempotencyAndArchiveCleanupScheduler(IdempotencyStoreService idempotencyStoreService) {
        this(idempotencyStoreService, (CompletedEventPublications) null, DEFAULT_COMPLETED_EVENT_RETENTION_DAYS);
    }

    @Scheduled(cron = "${app.cleanup.cron:0 0 2 * * *}", zone = "${app.cleanup.zone:Asia/Ho_Chi_Minh}")
    @SchedulerLock(name = "IdempotencyAndArchiveCleanupScheduler", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public int cleanupExpiredIdempotencyKeysAndArchive() {
        Instant now = Instant.now();
        int deletedKeys = idempotencyStoreService.cleanupExpiredKeys(now);
        if (deletedKeys > 0) {
            log.info("IdempotencyAndArchiveCleanupScheduler deleted {} expired idempotency key(s)", deletedKeys);
        }

        if (completedEventPublications != null && completedEventRetentionDays > 0) {
            completedEventPublications.deletePublicationsOlderThan(Duration.ofDays(completedEventRetentionDays));
        }

        return deletedKeys;
    }
}
