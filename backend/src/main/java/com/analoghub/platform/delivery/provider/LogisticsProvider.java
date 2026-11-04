package com.analoghub.platform.delivery.provider;

import com.analoghub.platform.delivery.domain.DeliveryTask;
import com.analoghub.platform.delivery.domain.LogisticsProviderType;

import java.math.BigDecimal;

public interface LogisticsProvider {

    LogisticsProviderType providerType();

    BigDecimal estimateFee(double distanceKm, boolean twoWay);

    ProviderDispatchResult dispatch(DeliveryTask task);

    void cancel(DeliveryTask task, String reason);
}
