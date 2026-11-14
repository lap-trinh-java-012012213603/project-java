package com.analoghub.platform.booking.facade;

import com.analoghub.platform.booking.domain.LabOrderItem;
import com.analoghub.platform.booking.dto.LabOrderResponse;
import com.analoghub.platform.booking.repository.LabOrderItemRepository;
import com.analoghub.platform.booking.service.LabOrderService;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingOrderFacadeImpl implements BookingOrderFacade {

    private final LabOrderService labOrderService;
    private final LabOrderItemRepository orderItemRepository;

    @Override
    @Transactional(readOnly = true)
    public LabOrderResponse getOrderDetails(UUID orderId) {
        return labOrderService.getOrderById(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getUnitPriceForOrderItem(UUID orderItemId) {
        return orderItemRepository.findById(orderItemId)
                .map(LabOrderItem::getUnitPriceSnapshotVnd)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy Order Item: " + orderItemId));
    }

    @Override
    @Transactional
    public void updateOrderStatus(UUID orderId, String newStatus) {
        labOrderService.transitionOrderStatus(orderId, newStatus);
    }
}