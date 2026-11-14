package com.analoghub.platform.delivery.scheduler;

import com.analoghub.platform.delivery.domain.DeliveryTask;
import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import com.analoghub.platform.delivery.domain.DeliveryTrackingLog;
import com.analoghub.platform.delivery.repository.DeliveryTaskRepository;
import com.analoghub.platform.delivery.repository.DeliveryTrackingLogRepository;
import com.analoghub.platform.delivery.service.DeliveryEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTimeoutScheduler {

    private static final Set<DeliveryTaskStatus> PENDING_PICKUP_STATUSES = EnumSet.of(
            DeliveryTaskStatus.CREATED,
            DeliveryTaskStatus.ASSIGNING_DRIVER
    );

    private final DeliveryTaskRepository taskRepository;
    private final DeliveryTrackingLogRepository trackingLogRepository;
    private final DeliveryEventPublisher deliveryEventPublisher;

    @Scheduled(fixedDelayString = "${analoghub.delivery.timeout-scan-ms:180000}")
    @SchedulerLock(name = "DeliveryTimeoutScheduler", lockAtMostFor = "PT2M50S", lockAtLeastFor = "PT10S")
    @Transactional
    public void scanAndExpireOverdueTasks() {
        Instant now = Instant.now();
        List<DeliveryTask> overdueTasks = taskRepository.findByStatusInAndPickupDeadlineAtBefore(PENDING_PICKUP_STATUSES, now);
        if (overdueTasks.isEmpty()) {
            return;
        }

        for (DeliveryTask task : overdueTasks) {
            task.setStatus(DeliveryTaskStatus.TIMEOUT_FAILED);
            taskRepository.save(task);

            String reason = "Pickup deadline exceeded without driver assignment";
            trackingLogRepository.save(DeliveryTrackingLog.builder()
                    .deliveryTaskId(task.getId())
                    .status(DeliveryTaskStatus.TIMEOUT_FAILED)
                    .note(reason)
                    .loggedAt(now)
                    .build());

            deliveryEventPublisher.publishStatusChangedEvent(task, reason);
        }
    }
}
