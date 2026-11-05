package com.analoghub.platform.payment.scheduler;

import com.analoghub.platform.payment.service.PaymentCheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduler #2: Scans every 2 minutes for {@code INITIATED} payment transactions whose
 * {@code expires_at} has passed, transitions them to {@code EXPIRED}, and emits {@code PaymentFailedEvent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationScheduler {

    private final PaymentCheckoutService paymentCheckoutService;

    @Scheduled(fixedDelayString = "${analoghub.payment.expiration-scan-ms:120000}")
    @SchedulerLock(name = "PaymentExpirationScheduler", lockAtMostFor = "PT1M50S", lockAtLeastFor = "PT10S")
    @Transactional
    public void scanAndExpireOverdueTransactions() {
        int expiredCount = paymentCheckoutService.expireOverdueInitiatedTransactions(Instant.now());
        if (expiredCount > 0) {
            log.info("PaymentExpirationScheduler expired {} overdue INITIATED payment transactions", expiredCount);
        }
    }
}
