package com.analoghub.platform.booking.scheduler;

import com.analoghub.platform.booking.domain.LabOrder;
import com.analoghub.platform.booking.domain.LabOrderItem;
import com.analoghub.platform.booking.repository.LabOrderItemRepository;
import com.analoghub.platform.booking.repository.LabOrderRepository;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnpaidOrderAutoCancelScheduler {

    private final LabOrderRepository labOrderRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final FilmLabFacade filmLabFacade;

    @Scheduled(fixedDelayString = "60000") // 1 phút
    @SchedulerLock(name = "UnpaidOrderAutoCancelScheduler_cancel", lockAtLeastFor = "PT15S", lockAtMostFor = "PT50S")
    @Transactional
    public void cancelExpiredUnpaidOrders() {
        Instant now = Instant.now();
        List<LabOrder> expiredOrders = labOrderRepository.findAll().stream()
                .filter(o -> "PENDING_PAYMENT".equals(o.getStatus()) && o.getPaymentExpiresAt() != null && o.getPaymentExpiresAt().isBefore(now))
                .toList();

        for (LabOrder order : expiredOrders) {
            order.setStatus("CANCELLED");
            labOrderRepository.save(order);

            List<LabOrderItem> items = labOrderItemRepository.findAll().stream().filter(i -> order.getId().equals(i.getOrderId())).toList();
            items.forEach(item -> filmLabFacade.releaseCapacity(order.getLabId(), item.getProcessType(), item.getRollQuantity()));
            log.info("Tự động hủy đơn {}", order.getOrderCode());
        }
    }
}