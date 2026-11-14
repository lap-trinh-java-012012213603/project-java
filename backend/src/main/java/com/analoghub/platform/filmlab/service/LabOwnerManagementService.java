package com.analoghub.platform.filmlab.service;

import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.domain.ServicePackage;
import com.analoghub.platform.filmlab.dto.ServicePackageRequest;
import com.analoghub.platform.filmlab.dto.UpdateCapacityRuleRequest;
import com.analoghub.platform.filmlab.dto.UpdateLabProfileRequest;
import com.analoghub.platform.filmlab.repository.LabCapacityRuleRepository;
import com.analoghub.platform.filmlab.repository.LabRepository;
import com.analoghub.platform.filmlab.repository.ServicePackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LabOwnerManagementService {

    private final LabRepository labRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final LabCapacityRuleRepository labCapacityRuleRepository;

    @Transactional
    public Lab updateLabProfile(UUID labId, UpdateLabProfileRequest request) {
        Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        lab.setName(request.getName());
        lab.setBio(request.getBio());
        lab.setPhone(request.getPhone());
        lab.setFullAddress(request.getFullAddress());
        lab.setDistrictCode(request.getDistrictCode());
        lab.setProvinceCode(request.getProvinceCode());

        return labRepository.save(lab);
    }

    @Transactional
    public ServicePackage addServicePackage(UUID labId, ServicePackageRequest request) {
        Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        // Yêu cầu nhóm trưởng: Gộp devFee và scanFee thành unitPriceVnd cho khớp DB
        BigDecimal unitPrice = request.getDevFeeVnd().add(request.getScanFeeVnd());

        ServicePackage servicePackage = ServicePackage.builder()
                .labId(lab.getId())
                .categoryCode(request.getCategoryCode())
                .name(request.getName())
                .filmFormat(request.getFilmFormat())
                .processType(request.getProcessType())
                .scanResolution(request.getScanResolution())
                .unitPriceVnd(unitPrice)
                .estimatedHours(request.getEstimatedHours())
                .isActive(true)
                .build();

        return servicePackageRepository.save(servicePackage);
    }

    @Transactional
    public LabCapacityRule updateCapacityRule(UUID labId, UpdateCapacityRuleRequest request) {
        if (!labRepository.existsById(labId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // Tìm rule cũ, nếu chưa có thì tạo mới
        LabCapacityRule rule = labCapacityRuleRepository.findByLabIdAndProcessType(labId, request.getProcessType())
                .orElse(LabCapacityRule.builder()
                        .labId(labId)
                        .processType(request.getProcessType())
                        .build());

        rule.setMaxDailyRolls(request.getMaxDailyRolls());
        rule.setBaseTurnaroundHours(request.getBaseTurnaroundHours());
        rule.setPenaltyHoursPerBatch(request.getPenaltyHoursPerBatch());
        rule.setAcceptingOrders(request.isAcceptingOrders());

        return labCapacityRuleRepository.save(rule);
    }
}