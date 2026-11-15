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
    @SchedulerLock(name = "UnpaidOrderAutoCancelScheduler_cancelExpiredOrders", lockAtLeastFor = "PT15S", lockAtMostFor = "PT50S")
    @Transactional
    public void cancelExpiredUnpaidOrders() {
        Instant now = Instant.now();
        List<LabOrder> expiredOrders = labOrderRepository.findByStatusAndPaymentExpiresAtBefore("PENDING_PAYMENT", now);

        for (LabOrder order : expiredOrders) {
            order.setStatus("CANCELLED");
            labOrderRepository.save(order);

            List<LabOrderItem> items = labOrderItemRepository.findByOrderId(order.getId());
            for (LabOrderItem item : items) {
                filmLabFacade.releaseCapacity(order.getLabId(), item.getProcessType(), item.getRollQuantity());
            }

            log.info("Tự động hủy đơn quá hạn thanh toán 15 phút: {} và nhả {} cuộn công suất",
                    order.getOrderCode(), order.getTotalRolls());
        }
    }
}
