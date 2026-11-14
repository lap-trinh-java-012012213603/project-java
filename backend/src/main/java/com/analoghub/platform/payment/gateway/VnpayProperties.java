package com.analoghub.platform.payment.gateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.vnpay")
public class VnpayProperties {

    private String tmnCode = "ANALOG01";
    private String hashSecret = "ANALOGHUB_DEFAULT_VNPAY_SECRET_KEY";
    private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String returnUrl = "http://localhost:5173/payment/vnpay-return";
    private int timeoutMinutes = 15;
}
