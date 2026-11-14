package com.analoghub.platform.booking.facade;

import com.analoghub.platform.booking.dto.LabOrderResponse;
import java.math.BigDecimal;
import java.util.UUID;

public interface BookingOrderFacade {
    LabOrderResponse getOrderDetails(UUID orderId);
    BigDecimal getUnitPriceForOrderItem(UUID orderItemId);
    void updateOrderStatus(UUID orderId, String newStatus);
}