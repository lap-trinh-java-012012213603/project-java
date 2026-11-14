package com.analoghub.platform.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayIpnResponse(
        @JsonProperty("RspCode")
        String rspCode,

        @JsonProperty("Message")
        String message
) {
    public static VnpayIpnResponse confirmSuccess() {
        return new VnpayIpnResponse("00", "Confirm Success");
    }

    public static VnpayIpnResponse orderNotFound() {
        return new VnpayIpnResponse("01", "Order not found");
    }

    public static VnpayIpnResponse alreadyConfirmed() {
        return new VnpayIpnResponse("02", "Order already confirmed");
    }

    public static VnpayIpnResponse invalidAmount() {
        return new VnpayIpnResponse("04", "Invalid amount");
    }

    public static VnpayIpnResponse invalidChecksum() {
        return new VnpayIpnResponse("97", "Invalid Checksum");
    }

    public static VnpayIpnResponse unknownError() {
        return new VnpayIpnResponse("99", "Unknown error");
    }
}
