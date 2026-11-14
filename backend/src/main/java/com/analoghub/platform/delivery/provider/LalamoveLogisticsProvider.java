package com.analoghub.platform.delivery.provider;

import com.analoghub.platform.delivery.domain.DeliveryTask;
import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import com.analoghub.platform.delivery.domain.LogisticsProviderType;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LalamoveLogisticsProvider implements LogisticsProvider {

    private static final BigDecimal DEFAULT_BASE_FEE = new BigDecimal("24000.00");
    private static final BigDecimal DEFAULT_PER_KM_FEE = new BigDecimal("5200.00");

    private final PlatformConfigFacade platformConfigFacade;

    @Override
    public LogisticsProviderType providerType() {
        return LogisticsProviderType.LALAMOVE;
    }

    @Override
    public BigDecimal estimateFee(double distanceKm, boolean twoWay) {
        BigDecimal baseFee = platformConfigFacade.getDecimalOrDefault("delivery.lalamove.base_fee_vnd", DEFAULT_BASE_FEE);
        BigDecimal perKmFee = platformConfigFacade.getDecimalOrDefault("delivery.lalamove.per_km_fee_vnd", DEFAULT_PER_KM_FEE);
        double extraKm = Math.max(0.0, distanceKm - 2.0);
        BigDecimal oneWay = baseFee.add(perKmFee.multiply(BigDecimal.valueOf(extraKm)));
        BigDecimal total = twoWay ? oneWay.multiply(BigDecimal.valueOf(2)) : oneWay;
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public ProviderDispatchResult dispatch(DeliveryTask task) {
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String trackingId = "LLM-" + shortId;
        BigDecimal fee = task.getShippingFeeVnd() != null ? task.getShippingFeeVnd() : DEFAULT_BASE_FEE;
        return new ProviderDispatchResult(trackingId, DeliveryTaskStatus.ASSIGNING_DRIVER, fee);
    }

    @Override
    public void cancel(DeliveryTask task, String reason) {
    }
}
