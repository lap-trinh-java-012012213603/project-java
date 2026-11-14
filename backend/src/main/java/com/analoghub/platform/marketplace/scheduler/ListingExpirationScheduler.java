package com.analoghub.platform.marketplace.scheduler;

import com.analoghub.platform.marketplace.service.MarketplaceListingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListingExpirationScheduler {

    private final MarketplaceListingService listingService;

    @Scheduled(fixedDelayString = "${app.marketplace.expiration-scan-ms:3600000}")
    @SchedulerLock(
            name = "marketplaceListingExpirationScheduler",
            lockAtLeastFor = "PT30S",
            lockAtMostFor = "PT10M"
    )
    public void scanAndExpireListings() {
        int expiredCount = listingService.expireOverdueListings();
        if (expiredCount > 0) {
            log.info("Expired {} overdue marketplace listings", expiredCount);
        }
    }
}
