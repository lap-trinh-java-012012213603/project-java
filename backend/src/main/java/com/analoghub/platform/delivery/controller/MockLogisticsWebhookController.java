package com.analoghub.platform.delivery.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.delivery.dto.MockLogisticsWebhookRequest;
import com.analoghub.platform.delivery.dto.MockWebhookSimulatedResponse;
import com.analoghub.platform.delivery.service.LogisticsWebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!prod")
@RequestMapping("/api/v1/webhooks/logistics")
@RequiredArgsConstructor
public class MockLogisticsWebhookController {

    private final LogisticsWebhookService webhookService;

    @PostMapping("/mock")
    @RateLimit(keyPrefix = "webhook:mock", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<MockWebhookSimulatedResponse>> handleMock(
            @Valid @RequestBody MockLogisticsWebhookRequest request,
            @RequestHeader(value = "X-Mock-Secret", required = false) String mockSecret
    ) {
        MockWebhookSimulatedResponse result = webhookService.processMockWebhook(request, mockSecret);
        return ResponseEntity.ok(ApiResponse.success("Mock logistics webhook simulated", result));
    }
}
