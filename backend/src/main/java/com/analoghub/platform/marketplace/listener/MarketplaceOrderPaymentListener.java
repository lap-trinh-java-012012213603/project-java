package com.analoghub.platform.marketplace.listener;

import com.analoghub.platform.marketplace.service.MarketplaceOrderService;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketplaceOrderPaymentListener {

    private final MarketplaceOrderService orderService;

    @ApplicationModuleListener
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        orderService.handlePaymentSucceeded(event);
    }
}
