package com.analoghub.platform.delivery.facade;

import com.analoghub.platform.delivery.dto.CreateDeliveryTaskRequest;
import com.analoghub.platform.delivery.dto.ShippingFeeEstimateDto;
import com.analoghub.platform.delivery.dto.ShippingFeeEstimateRequest;
import java.math.BigDecimal;
import java.util.UUID;

public interface DeliveryFacade {

    UUID createDeliveryTask(CreateDeliveryTaskRequest request);

    void cancelDeliveryTask(UUID taskId, String reason);

    ShippingFeeEstimateDto estimateShippingFee(ShippingFeeEstimateRequest request);

    default BigDecimal estimateShippingFee(double pickupLat, double pickupLon, double dropoffLat, double dropoffLon, boolean twoWay) {
        return estimateShippingFee(new ShippingFeeEstimateRequest(pickupLat, pickupLon, dropoffLat, dropoffLon, "PICKUP_TO_LAB", twoWay))
                .estimatedFeeVnd();
    }
}
