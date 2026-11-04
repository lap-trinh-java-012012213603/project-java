package com.analoghub.platform.delivery.provider;

import com.analoghub.platform.delivery.domain.DeliveryTask;
import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import com.analoghub.platform.delivery.domain.LogisticsProviderType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@Profile("!prod")
public class MockLogisticsProvider implements LogisticsProvider {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final BigDecimal BASE_FEE = new BigDecimal("20000.00");
    private static final BigDecimal PER_KM_FEE = new BigDecimal("5000.00");

    @Override
    public LogisticsProviderType providerType() {
        return LogisticsProviderType.MOCK;
    }

    @Override
    public BigDecimal estimateFee(double distanceKm, boolean twoWay) {
        double extraKm = Math.max(0.0, distanceKm - 2.0);
        BigDecimal oneWay = BASE_FEE.add(PER_KM_FEE.multiply(BigDecimal.valueOf(extraKm)));
        BigDecimal total = twoWay ? oneWay.multiply(BigDecimal.valueOf(2)) : oneWay;
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public ProviderDispatchResult dispatch(DeliveryTask task) {
        String datePart = LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
        String shortId = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String trackingId = "MOCK-" + datePart + "-" + shortId;
        BigDecimal fee = task.getShippingFeeVnd() != null ? task.getShippingFeeVnd() : BASE_FEE;
        return new ProviderDispatchResult(trackingId, DeliveryTaskStatus.ASSIGNING_DRIVER, fee);
    }

    @Override
    public void cancel(DeliveryTask task, String reason) {
    }
}
