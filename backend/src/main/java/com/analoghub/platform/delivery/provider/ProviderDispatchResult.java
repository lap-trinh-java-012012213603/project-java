package com.analoghub.platform.delivery.provider;

import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import java.math.BigDecimal;

public record ProviderDispatchResult(
        String externalTrackingId,
        DeliveryTaskStatus initialStatus,
        BigDecimal estimatedFeeVnd
) {
}
