package com.analoghub.platform.aiassistant.controller;

import com.analoghub.platform.aiassistant.dto.ScanDiagnosticResponse;
import com.analoghub.platform.aiassistant.dto.TriggerScanDiagnosticRequest;
import com.analoghub.platform.aiassistant.service.cv.ScanQualityCvService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.security.AuthenticatedUser;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/ai/scan-diagnostics")
@RequiredArgsConstructor
public class AiScanDiagnosticsController {

    private final ScanQualityCvService scanQualityCvService;

    /**
     * API 8: POST /api/v1/ai/scan-diagnostics/{frameId}
     * Chẩn đoán Sâu Chất lượng Bức Ảnh Scan 2 Tầng (Classical CV + Vision LLM).
     */
    @PostMapping("/{frameId}")
    @PreAuthorize("isAuthenticated()")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "ai:scan-qc:diagnose", capacity = 10, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ScanDiagnosticResponse>> diagnoseFrame(
            @PathVariable UUID frameId,
            @Valid @RequestBody(required = false) TriggerScanDiagnosticRequest request
    ) {
        AuthenticatedUser currentUser = SecurityContextUtils.getRequiredCurrentUser();
        ScanDiagnosticResponse response = scanQualityCvService.diagnoseFrame(
                currentUser.getUserId(),
                currentUser.isAdmin(),
                currentUser.getLabIds(),
                frameId,
                request != null ? request : TriggerScanDiagnosticRequest.builder().runVisionLlmStage2(true).build()
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Hoàn tất chẩn đoán chất lượng bức ảnh scan 2 tầng (Classical CV + Vision LLM).",
                response
        ));
    }
}
