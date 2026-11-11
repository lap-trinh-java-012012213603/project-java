package com.analoghub.platform.filmlab.controller;

import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.domain.ServicePackage;
import com.analoghub.platform.filmlab.dto.ServicePackageRequest;
import com.analoghub.platform.filmlab.dto.UpdateCapacityRuleRequest;
import com.analoghub.platform.filmlab.dto.UpdateLabProfileRequest;
import com.analoghub.platform.filmlab.service.LabOwnerManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/labs/{labId}/owner")
@RequiredArgsConstructor
public class LabOwnerController {

    private final LabOwnerManagementService labOwnerManagementService;

    @PutMapping("/profile")
    @PreAuthorize("@labTenantGuard.isLabOwner(authentication, #labId)")
    public ResponseEntity<Lab> updateLabProfile(
            @PathVariable UUID labId,
            @RequestBody UpdateLabProfileRequest request) {
        Lab updatedLab = labOwnerManagementService.updateLabProfile(labId, request);
        return ResponseEntity.ok(updatedLab);
    }

    @PostMapping("/packages")
    @PreAuthorize("@labTenantGuard.isLabOwner(authentication, #labId)")
    public ResponseEntity<ServicePackage> addServicePackage(
            @PathVariable UUID labId,
            @RequestBody ServicePackageRequest request) {
        ServicePackage newPackage = labOwnerManagementService.addServicePackage(labId, request);
        return ResponseEntity.ok(newPackage);
    }

    @PutMapping("/capacity-rules")
    @PreAuthorize("@labTenantGuard.isLabOwner(authentication, #labId)")
    public ResponseEntity<LabCapacityRule> updateCapacityRule(
            @PathVariable UUID labId,
            @RequestBody UpdateCapacityRuleRequest request) {
        LabCapacityRule updatedRule = labOwnerManagementService.updateCapacityRule(labId, request);
        return ResponseEntity.ok(updatedRule);
    }
}