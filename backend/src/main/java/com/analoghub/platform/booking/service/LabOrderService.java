package com.analoghub.platform.booking.service;

import com.analoghub.platform.booking.domain.LabOrder;
import com.analoghub.platform.booking.domain.LabOrderItem;
import com.analoghub.platform.booking.dto.CreateLabOrderRequest;
import com.analoghub.platform.booking.dto.LabOrderResponse;
import com.analoghub.platform.booking.event.LabOrderCreatedEvent;
import com.analoghub.platform.booking.repository.LabOrderItemRepository;
import com.analoghub.platform.booking.repository.LabOrderRepository;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.payment.facade.PaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabOrderService {

    private final LabOrderRepository labOrderRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final FilmLabFacade filmLabFacade;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private PaymentFacade paymentFacade;

    @Transactional
    public LabOrderResponse createOrder(UUID customerId, CreateLabOrderRequest request) {
        int totalRolls = 0;
        BigDecimal subtotalVnd = BigDecimal.ZERO;

        String orderCode = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        Instant now = Instant.now();
        Instant paymentExpiresAt = now.plusSeconds(15 * 60);

        LabOrder order = new LabOrder();
        order.setOrderCode(orderCode);
        order.setCustomerId(customerId);
        order.setLabId(request.labId());
        order.setStatus("PENDING_PAYMENT");
        order.setFulfillmentIn(request.fulfillmentIn());
        order.setFulfillmentOut(request.fulfillmentOut());
        order.setNegativeHandlingPolicy(request.negativeHandlingPolicy() != null ? request.negativeHandlingPolicy() : "HOLD_30_DAYS");
        order.setPaymentExpiresAt(paymentExpiresAt);

        List<LabOrderItem> itemsToSave = new ArrayList<>();

        for (CreateLabOrderRequest.OrderItemRequest itemReq : request.items()) {
            FilmLabFacade.ServicePackageInfo pkg = filmLabFacade.getServicePackageInfo(itemReq.servicePackageId());

            if (!pkg.labId().equals(request.labId())) {
                throw new IllegalArgumentException("Gói dịch vụ không thuộc Lab đã chọn");
            }

            filmLabFacade.reserveCapacity(request.labId(), pkg.processType(), itemReq.rollQuantity());

            totalRolls += itemReq.rollQuantity();
            subtotalVnd = subtotalVnd.add(pkg.unitPriceVnd().multiply(BigDecimal.valueOf(itemReq.rollQuantity())));

            LabOrderItem orderItem = new LabOrderItem();
            orderItem.setServicePackageId(pkg.id());
            orderItem.setFilmStockId(itemReq.filmStockId());
            orderItem.setPackageNameSnapshot(pkg.name());
            orderItem.setFilmFormat(pkg.filmFormat());
            orderItem.setProcessType(pkg.processType());
            orderItem.setScanResolution(pkg.scanResolution() != null ? pkg.scanResolution() : "NONE");
            orderItem.setRollQuantity(itemReq.rollQuantity());
            orderItem.setUnitPriceSnapshotVnd(pkg.unitPriceVnd());
            orderItem.setPushPullStops(itemReq.pushPullStops());
            orderItem.setSpecialInstructions(itemReq.specialInstructions());
            itemsToSave.add(orderItem);
        }

        BigDecimal deliveryFee = "PICKUP_DELIVERY".equals(request.fulfillmentIn()) || "RETURN_DELIVERY_NEGATIVE".equals(request.fulfillmentOut()) ? BigDecimal.valueOf(30000) : BigDecimal.ZERO;

        order.setTotalRolls(totalRolls);
        order.setSubtotalVnd(subtotalVnd);
        order.setDeliveryFeeVnd(deliveryFee);
        order.setDiscountVnd(BigDecimal.ZERO);
        order.setTotalAmountVnd(subtotalVnd.add(deliveryFee));
        order.setEstimatedCompletionAt(now.plusSeconds(24 * 3600L)); // Mặc định 24h

        LabOrder savedOrder = labOrderRepository.save(order);
        itemsToSave.forEach(item -> item.setOrderId(savedOrder.getId()));
        List<LabOrderItem> savedItems = labOrderItemRepository.saveAll(itemsToSave);

        eventPublisher.publishEvent(new LabOrderCreatedEvent(
                savedOrder.getId(), savedOrder.getOrderCode(), savedOrder.getCustomerId(),
                savedOrder.getLabId(), savedOrder.getTotalRolls(), savedOrder.getTotalAmountVnd(),
                savedOrder.getPaymentExpiresAt() != null ? savedOrder.getPaymentExpiresAt().atOffset(ZoneOffset.UTC) : null
        ));

        return mapToResponse(savedOrder, savedItems);
    }

    @Transactional
    public LabOrderResponse cancelOrderByCustomer(UUID customerId, UUID orderId, String reason) {
        LabOrder order = labOrderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn"));
        if (!order.getCustomerId().equals(customerId)) throw new IllegalArgumentException("Không có quyền");
        if (!"PENDING_PAYMENT".equals(order.getStatus()) && !"PAID_WAITING_FILM".equals(order.getStatus())) throw new IllegalArgumentException("Không thể hủy");

        boolean wasPaid = "PAID_WAITING_FILM".equals(order.getStatus());
        order.setStatus("CANCELLED");
        LabOrder saved = labOrderRepository.save(order);

        List<LabOrderItem> items = labOrderItemRepository.findAll().stream().filter(i -> orderId.equals(i.getOrderId())).toList();
        items.forEach(item -> filmLabFacade.releaseCapacity(order.getLabId(), item.getProcessType(), item.getRollQuantity()));

        if (wasPaid && paymentFacade != null) paymentFacade.refundEscrow(orderId, reason, "CUSTOMER_CANCEL");
        return mapToResponse(saved, items);
    }

    @Transactional
    public LabOrderResponse rejectOrderByLab(UUID labId, UUID orderId, String reason) {
        LabOrder order = labOrderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn"));
        if (!order.getLabId().equals(labId)) throw new IllegalArgumentException("Không có quyền");
        if (!"PENDING_PAYMENT".equals(order.getStatus()) && !"PAID_WAITING_FILM".equals(order.getStatus())) throw new IllegalArgumentException("Không thể từ chối");

        boolean wasPaid = "PAID_WAITING_FILM".equals(order.getStatus());
        order.setStatus("CANCELLED");
        LabOrder saved = labOrderRepository.save(order);

        List<LabOrderItem> items = labOrderItemRepository.findAll().stream().filter(i -> orderId.equals(i.getOrderId())).toList();
        items.forEach(item -> filmLabFacade.releaseCapacity(order.getLabId(), item.getProcessType(), item.getRollQuantity()));

        if (wasPaid && paymentFacade != null) paymentFacade.refundEscrow(orderId, reason, "LAB_REJECT");
        return mapToResponse(saved, items);
    }

    @Transactional(readOnly = true)
    public LabOrderResponse getOrderById(UUID orderId) {
        LabOrder order = labOrderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn"));
        List<LabOrderItem> items = labOrderItemRepository.findAll().stream().filter(i -> orderId.equals(i.getOrderId())).toList();
        return mapToResponse(order, items);
    }

    @Transactional
    public void transitionOrderStatus(UUID orderId, String newStatus) {
        LabOrder order = labOrderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn"));
        order.setStatus(newStatus);
        labOrderRepository.save(order);
    }

    private LabOrderResponse mapToResponse(LabOrder order, List<LabOrderItem> items) {
        List<LabOrderResponse.OrderItemDto> itemDtos = items.stream().map(i -> new LabOrderResponse.OrderItemDto(
                i.getId(), i.getServicePackageId(), i.getFilmStockId(), i.getPackageNameSnapshot(),
                i.getFilmFormat(), i.getProcessType(), i.getScanResolution(), i.getRollQuantity(),
                i.getUnitPriceSnapshotVnd(), i.getPushPullStops(), i.getSpecialInstructions())).toList();

        return new LabOrderResponse(
                order.getId(), order.getOrderCode(), order.getCustomerId(), order.getLabId(), order.getStatus(),
                order.getFulfillmentIn(), order.getFulfillmentOut(), order.getNegativeHandlingPolicy(),
                order.getTotalRolls(), order.getSubtotalVnd(), order.getDeliveryFeeVnd(), order.getDiscountVnd(), order.getTotalAmountVnd(),
                order.getEstimatedCompletionAt() != null ? order.getEstimatedCompletionAt().atOffset(ZoneOffset.UTC) : null,
                order.getPaymentExpiresAt() != null ? order.getPaymentExpiresAt().atOffset(ZoneOffset.UTC) : null,
                order.getCreatedAt() != null ? order.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
                itemDtos
        );
    }
}