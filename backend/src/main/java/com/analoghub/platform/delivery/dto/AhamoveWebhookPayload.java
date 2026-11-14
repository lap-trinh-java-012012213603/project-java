package com.analoghub.platform.delivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AhamoveWebhookPayload(
        @JsonProperty("_id") String id,
        String status,
        @JsonProperty("sub_status") String subStatus,
        @JsonProperty("supplier_name") String supplierName,
        @JsonProperty("supplier_id") String supplierId,
        Double lat,
        Double lng,
        @JsonProperty("pod_image_url") String podImageUrl,
        Long timestamp
) {
}
