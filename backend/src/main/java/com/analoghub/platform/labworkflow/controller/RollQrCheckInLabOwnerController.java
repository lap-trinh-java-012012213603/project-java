package com.analoghub.platform.labworkflow.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.labworkflow.dto.FilmRollResponse;
import com.analoghub.platform.labworkflow.dto.QrCheckInRequest;
import com.analoghub.platform.labworkflow.service.RollQrCheckInService;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my-labs/{labId}/rolls")
@RequiredArgsConstructor
public class RollQrCheckInLabOwnerController {

    private final RollQrCheckInService rollQrCheckInService;

    @PostMapping("/qr-checkin")
    public ResponseEntity<ApiResponse<FilmRollResponse>> checkInRoll(
            @PathVariable UUID labId,
            @Valid @RequestBody QrCheckInRequest request
    ) {
        UUID operatorId = SecurityContextUtils.getCurrentUserId().orElse(null);
        FilmRollResponse response = rollQrCheckInService.checkInRollByQr(labId, operatorId, request);
        return ResponseEntity.ok(ApiResponse.success("Check-in cuộn film thành công", response));
    }
}