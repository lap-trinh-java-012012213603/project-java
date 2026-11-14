package com.analoghub.platform.payment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePayoutRequest(
        @NotNull(message = "amountVnd is required")
        @DecimalMin(value = "100000.00", message = "Minimum payout amount is 100,000 VND")
        @DecimalMax(value = "200000000.00", message = "Maximum payout amount is 200,000,000 VND")
        @Digits(integer = 13, fraction = 2)
        BigDecimal amountVnd,

        @NotBlank(message = "bankCode is required")
        @Size(min = 2, max = 32)
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "bankCode must contain only uppercase letters, digits, or underscores")
        String bankCode,

        @NotBlank(message = "bankAccountNo is required")
        @Pattern(regexp = "^[0-9]{6,25}$", message = "bankAccountNo must be 6 to 25 digits")
        String bankAccountNo,

        @NotBlank(message = "bankAccountName is required")
        @Size(min = 3, max = 120)
        @Pattern(regexp = "^[A-Z ]+$", message = "bankAccountName must be uppercase ASCII letters and spaces")
        String bankAccountName
) {
}
