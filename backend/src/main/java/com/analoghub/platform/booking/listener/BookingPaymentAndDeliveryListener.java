package com.analoghub.platform.booking.listener;

import com.analoghub.platform.booking.repository.LabOrderItemRepository;
import com.analoghub.platform.booking.repository.LabOrderRepository;
import com.analoghub.platform.delivery.event.FilmBagDeliveredToLabEvent;
import com.analoghub.platform.delivery.event.FilmBagPickedUpEvent;
import com.analoghub.platform.labworkflow.dto.FilmRollResponse;
import com.analoghub.platform.labworkflow.event.FilmRollStatusChangedEvent;
import com.analoghub.platform.labworkflow.facade.LabWorkflowFacade;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingPaymentAndDeliveryListener {
    private static final Set<String> TERMINAL_ROLL_STATUSES = Set.of(
            "COMPLETED", "BLANK_ROLL_DETECTED", "NEGATIVE_RETURNED"
    );

    private final LabOrderRepository labOrderRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final LabWorkflowFacade labWorkflowFacade;

    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (!"LAB_ORDER".equalsIgnoreCase(String.valueOf(event.paymentPurpose()))) return;
        labOrderRepository.findById(event.referenceId()).ifPresent(order -> {
            if (!"PENDING_PAYMENT".equals(order.getStatus())) return;
            order.setStatus("PAID_WAITING_FILM");
            labOrderRepository.save(order);

            List<LabWorkflowFacade.OrderItemRollSpec> specs = labOrderItemRepository.findAll().stream()
                    .filter(i -> order.getId().equals(i.getOrderId()))
                    .map(i -> new LabWorkflowFacade.OrderItemRollSpec(i.getId(), i.getFilmFormat(), i.getProcessType(), i.getScanResolution(), i.getPushPullStops(), i.getRollQuantity()))
                    .toList();

            labWorkflowFacade.initializeRollsForOrder(order.getId(), order.getLabId(), order.getCustomerId(), specs);
            log.info("Đã khởi tạo cuộn film cho đơn {}", order.getOrderCode());
        });
    }

    @EventListener
    @Transactional
    public void onFilmBagPickedUp(FilmBagPickedUpEvent event) {
        labOrderRepository.findById(event.orderId()).ifPresent(order -> {
            order.setStatus("IN_DELIVERY_TO_LAB");
            labOrderRepository.save(order);
        });
    }

    @EventListener
    @Transactional
    public void onFilmBagDeliveredToLab(FilmBagDeliveredToLabEvent event) {
        labOrderRepository.findById(event.orderId()).ifPresent(order -> {
            order.setStatus("IN_LAB_PROCESSING");
            labOrderRepository.save(order);
        });
    }

    @EventListener
    @Transactional
    public void onFilmRollStatusChanged(FilmRollStatusChangedEvent event) {
        if (event.orderId() == null) {
            return;
        }
        if ("CHECKED_IN".equals(event.toStatus())) {
            labOrderRepository.findById(event.orderId()).ifPresent(order -> {
                if ("PAID_WAITING_FILM".equals(order.getStatus()) || "IN_DELIVERY_TO_LAB".equals(order.getStatus())) {
                    order.setStatus("IN_LAB_PROCESSING");
                    labOrderRepository.save(order);
                }
            });
            return;
        }
        if (TERMINAL_ROLL_STATUSES.contains(event.toStatus())) {
            List<FilmRollResponse> rolls = labWorkflowFacade.getRollsByOrderId(event.orderId());
            if (!rolls.isEmpty() && rolls.stream().allMatch(r -> TERMINAL_ROLL_STATUSES.contains(r.status()))) {
                labOrderRepository.findById(event.orderId()).ifPresent(order -> {
                    order.setStatus("SCAN_DELIVERED");
                    labOrderRepository.save(order);
                });
            }
        }
    }
}