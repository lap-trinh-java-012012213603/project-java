package com.analoghub.platform.payment.controller;

import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.payment.dto.VnpayIpnResponse;
import com.analoghub.platform.payment.service.PaymentCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class VnpayWebhookController {

    private final PaymentCheckoutService paymentCheckoutService;

    @RequestMapping(
            value = "/api/v1/webhooks/payment/vnpay-ipn",
            method = {RequestMethod.GET, RequestMethod.POST}
    )
    @RateLimit(keyPrefix = "webhook:vnpay", capacity = 300, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<VnpayIpnResponse> handleIpn(
            @RequestParam Map<String, String> params
    ) {
        VnpayIpnResponse response = paymentCheckoutService.processVnpayIpn(params);
        return ResponseEntity.ok(response);
    }
}
