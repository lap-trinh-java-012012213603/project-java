package com.analoghub.platform.marketplace.listener;

import com.analoghub.platform.delivery.event.DeliveryFailedEvent;
import com.analoghub.platform.delivery.event.MarketParcelDeliveredEvent;
import com.analoghub.platform.marketplace.service.MarketplaceOrderService;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketplacePaymentAndDeliveryListener {

    private final MarketplaceOrderService orderService;

    @ApplicationModuleListener
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        orderService.handlePaymentSucceeded(event);
    }

    @ApplicationModuleListener
    public void onMarketParcelDelivered(MarketParcelDeliveredEvent event) {
        orderService.handleParcelDelivered(event);
    }

    @ApplicationModuleListener
    public void onDeliveryFailed(DeliveryFailedEvent event) {
        orderService.handleDeliveryFailed(event);
    }
}
