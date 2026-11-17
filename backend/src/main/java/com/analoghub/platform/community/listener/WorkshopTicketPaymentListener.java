package com.analoghub.platform.community.listener;

import com.analoghub.platform.community.service.WorkshopService;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkshopTicketPaymentListener {

    private final WorkshopService workshopService;

    @ApplicationModuleListener
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        workshopService.handleTicketPaymentSucceeded(event);
    }
}
