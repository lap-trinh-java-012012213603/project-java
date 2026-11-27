package com.analoghub.platform.booking.listener;

import com.analoghub.platform.booking.domain.LabOrderItem;
import com.analoghub.platform.booking.repository.LabOrderItemRepository;
import com.analoghub.platform.booking.repository.LabOrderRepository;
import com.analoghub.platform.delivery.event.FilmBagDeliveredToLabEvent;
import com.analoghub.platform.delivery.event.FilmBagPickedUpEvent;
import com.analoghub.platform.labworkflow.event.BlankRollDetectedEvent;
import com.analoghub.platform.labworkflow.event.FilmRollStatusChangedEvent;
import com.analoghub.platform.labworkflow.facade.LabWorkflowFacade;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import com.analoghub.platform.payment.facade.PaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingPaymentAndDeliveryListener {

    private final LabOrderRepository labOrderRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final LabWorkflowFacade labWorkflowFacade;
    private final PaymentFacade paymentFacade;

    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (!"LAB_ORDER".equalsIgnoreCase(String.valueOf(event.paymentPurpose()))) {
            return;
        }
        labOrderRepository.findById(event.referenceId()).ifPresent(order -> {
            if (!"PENDING_PAYMENT".equals(order.getStatus())) {
                return;
            }
            order.setStatus("PAID_WAITING_FILM");
            labOrderRepository.save(order);

            List<LabOrderItem> items = labOrderItemRepository.findByOrderId(order.getId());
            List<LabWorkflowFacade.OrderItemRollSpec> specs = items.stream()
                    .map(i -> new LabWorkflowFacade.OrderItemRollSpec(
                            i.getId(),
                            i.getFilmFormat(),
                            i.getProcessType(),
                            i.getScanResolution(),
                            i.getPushPullStops(),
                            i.getRollQuantity()
                    ))
                    .toList();

            labWorkflowFacade.initializeRollsForOrder(order.getId(), order.getLabId(), order.getCustomerId(), specs);
            log.info("Đơn hàng {} đã chuyển sang PAID_WAITING_FILM và khởi tạo {} cuộn film.",
                    order.getOrderCode(), order.getTotalRolls());
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
        labOrderRepository.findById(event.orderId()).ifPresent(order -> {
            if ("CHECKED_IN".equals(event.toStatus()) && !"IN_LAB_PROCESSING".equals(order.getStatus())) {
                order.setStatus("IN_LAB_PROCESSING");
                labOrderRepository.save(order);
            } else if ("COMPLETED".equals(event.toStatus()) || "BLANK_ROLL_DETECTED".equals(event.toStatus())) {
                if (labWorkflowFacade.areAllRollsCompletedForOrder(event.orderId())) {
                    order.setStatus("SCAN_DELIVERED");
                    labOrderRepository.save(order);
                }
            }
        });
    }

    @EventListener
    @Transactional
    public void onBlankRollDetected(BlankRollDetectedEvent event) {
        try {
            BigDecimal refundFee = BigDecimal.valueOf(30000);
            if (event.orderItemId() != null) {
                refundFee = labOrderItemRepository.findById(event.orderItemId())
                        .map(LabOrderItem::getUnitPriceSnapshotVnd)
                        .orElse(BigDecimal.valueOf(30000));
            }
            paymentFacade.partialRefundBlankRoll(
                    event.orderId(),
                    event.rollId(),
                    event.customerId(),
                    refundFee,
                    "Hoàn phí scan cuộn film trắng: " + event.rollCode()
            );
        } catch (Exception e) {
            log.warn("Lỗi hoàn phí cuộn trắng {}: {}", event.rollCode(), e.getMessage());
        }
    }
}