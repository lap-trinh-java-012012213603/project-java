package com.analoghub.platform.marketplace.dto;

import jakarta.validation.constraints.Size;

public record ConfirmOrderReceivedRequest(
        @Size(max = 500) String inspectionNote
) {
}
