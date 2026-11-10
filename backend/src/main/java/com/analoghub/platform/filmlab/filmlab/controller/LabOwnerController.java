package com.analoghub.platform.filmlab.controller;

import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.service.LabOwnerManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owner/labs")
@RequiredArgsConstructor
public class LabOwnerController {

    private final LabOwnerManagementService labOwnerManagementService;

    /**
     * API cập nhật hồ sơ Lab dành cho LabOwner
     * Endpoint: PUT /api/v1/owner/labs/{labId}/profile
     */
    @PutMapping("/{labId}/profile")
    public ResponseEntity<Lab> updateLabProfile(
            @PathVariable UUID labId,
            @RequestBody UpdateLabProfileRequest request
    ) {
        Lab updatedLab = labOwnerManagementService.updateLabProfile(
                labId,
                request.name(),
                request.description(),
                request.phoneNumber(),
                request.address()
        );
        return ResponseEntity.ok(updatedLab);
    }

    /**
     * API cấu hình quy tắc công suất tối đa theo ngày (max_daily_rolls)
     * Endpoint: PUT /api/v1/owner/labs/{labId}/capacity-rule
     */
    @PutMapping("/{labId}/capacity-rule")
    public ResponseEntity<LabCapacityRule> updateCapacityRule(
            @PathVariable UUID labId,
            @RequestBody UpdateCapacityRuleRequest request
    ) {
        LabCapacityRule rule = labOwnerManagementService.updateCapacityRule(labId, request.maxDailyRolls());
        return ResponseEntity.ok(rule);
    }

    // Các DTO request nội bộ cho Controller
    public record UpdateLabProfileRequest(
            String name,
            String description,
            String phoneNumber,
            String address
    ) {}

    public record UpdateCapacityRuleRequest(
            int maxDailyRolls
    ) {}
}