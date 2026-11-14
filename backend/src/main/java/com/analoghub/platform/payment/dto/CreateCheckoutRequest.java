package com.analoghub.platform.payment.dto;

import com.analoghub.platform.payment.domain.GatewayProvider;
import com.analoghub.platform.payment.domain.PaymentPurpose;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCheckoutRequest(
        @NotNull(message = "paymentPurpose is required")
        PaymentPurpose paymentPurpose,

        @NotNull(message = "referenceId is required")
        UUID referenceId,

        @NotBlank(message = "referenceCode is required")
        @Size(max = 40, message = "referenceCode must not exceed 40 characters")
        @Pattern(regexp = "^[A-Z0-9\\-_]+$", message = "referenceCode must contain only uppercase alphanumeric, '-' or '_'")
        String referenceCode,

        @NotNull(message = "payeeUserId is required")
        UUID payeeUserId,

        @NotNull(message = "amountVnd is required")
        @DecimalMin(value = "10000.00", message = "Minimum payment amount is 10,000.00 VND")
        @DecimalMax(value = "500000000.00", message = "Maximum payment amount is 500,000,000.00 VND")
        @Digits(integer = 13, fraction = 2, message = "amountVnd format is invalid")
        BigDecimal amountVnd,

        @NotNull(message = "gatewayProvider is required")
        GatewayProvider gatewayProvider,

        @Size(max = 20, message = "bankCode must not exceed 20 characters")
        String bankCode,

        @Size(max = 500, message = "returnUrlOverride must not exceed 500 characters")
        String returnUrlOverride,

        @Pattern(regexp = "^(vn|en)$", message = "locale must be 'vn' or 'en'")
        String locale
) {
}
