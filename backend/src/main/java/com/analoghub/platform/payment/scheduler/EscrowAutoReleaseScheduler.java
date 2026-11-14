package com.analoghub.platform.payment.scheduler;

import com.analoghub.platform.payment.service.EscrowHoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduler #3: Scans every 15 minutes for Escrow holds whose 72-hour inspection window
 * ({@code auto_release_at <= now}) has elapsed without dispute ({@code status IN ('HELD', 'PARTIALLY_REFUNDED')})
 * and automatically releases the net payout to the Lab/Seller wallet while deducting platform commission.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscrowAutoReleaseScheduler {

    private final EscrowHoldService escrowHoldService;

    @Scheduled(fixedDelayString = "${analoghub.payment.escrow-auto-release-scan-ms:900000}")
    @SchedulerLock(name = "EscrowAutoReleaseScheduler", lockAtMostFor = "PT14M", lockAtLeastFor = "PT30S")
    @Transactional
    public void scanAndAutoReleaseDueEscrows() {
        int releasedCount = escrowHoldService.autoReleaseDueEscrows(Instant.now());
        if (releasedCount > 0) {
            log.info("EscrowAutoReleaseScheduler auto-released {} due escrow holds", releasedCount);
        }
    }
}
