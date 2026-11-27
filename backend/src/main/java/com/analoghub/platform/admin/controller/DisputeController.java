package com.analoghub.platform.admin.controller;

import com.analoghub.platform.admin.dto.DisputeEvidenceDto;
import com.analoghub.platform.admin.dto.DisputeOpenedResponse;
import com.analoghub.platform.admin.dto.OpenDisputeRequest;
import com.analoghub.platform.admin.dto.SubmitDisputeEvidenceRequest;
import com.analoghub.platform.admin.service.DisputeResolutionService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/disputes")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeResolutionService disputeResolutionService;

    @PostMapping
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 24)
    @RateLimit(keyPrefix = "adm:disputes:open", capacity = 10, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DisputeOpenedResponse>> openDispute(
            @Valid @RequestBody OpenDisputeRequest request
    ) {
        DisputeOpenedResponse data = disputeResolutionService.openDispute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Đã mở hồ sơ khiếu nại tranh chấp và đóng băng khoản tiền tạm giữ Escrow thành công.",
                data
        ));
    }

    @PostMapping("/{id}/evidences")
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 24)
    @RateLimit(keyPrefix = "adm:disputes:evidence", capacity = 20, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DisputeEvidenceDto>> submitEvidence(
            @PathVariable("id") UUID disputeId,
            @Valid @RequestBody SubmitDisputeEvidenceRequest request
    ) {
        DisputeEvidenceDto data = disputeResolutionService.submitEvidence(disputeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Đã ghi nhận bằng chứng bổ sung vào hồ sơ tranh chấp.",
                data
        ));
    }
}
