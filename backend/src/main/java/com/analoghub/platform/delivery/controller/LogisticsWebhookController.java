package com.analoghub.platform.delivery.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.delivery.dto.WebhookProcessedResponse;
import com.analoghub.platform.delivery.service.LogisticsWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/logistics")
@RequiredArgsConstructor
public class LogisticsWebhookController {

    private final LogisticsWebhookService webhookService;

    @PostMapping("/ahamove")
    @RateLimit(keyPrefix = "webhook:ahamove", capacity = 300, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<WebhookProcessedResponse>> handleAhamove(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Aha-Signature", required = false) String signature
    ) {
        WebhookProcessedResponse result = webhookService.processAhamoveWebhook(rawBody, signature);
        return ResponseEntity.ok(ApiResponse.success("Ahamove webhook processed", result));
    }

    @PostMapping("/lalamove")
    @RateLimit(keyPrefix = "webhook:lalamove", capacity = 300, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<WebhookProcessedResponse>> handleLalamove(
            @RequestBody String rawBody,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        WebhookProcessedResponse result = webhookService.processLalamoveWebhook(rawBody, authorization);
        return ResponseEntity.ok(ApiResponse.success("Lalamove webhook processed", result));
    }
}
