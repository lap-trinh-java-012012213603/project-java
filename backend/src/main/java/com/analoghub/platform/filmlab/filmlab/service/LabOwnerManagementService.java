package com.analoghub.platform.filmlab.service;

import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.repository.LabCapacityRuleRepository;
import com.analoghub.platform.filmlab.repository.LabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LabOwnerManagementService {

    private final LabRepository labRepository;
    private final LabCapacityRuleRepository labCapacityRuleRepository;

    /**
     * Cập nhật thông tin hồ sơ của Lab (Dành cho chủ Lab)
     */
    @Transactional
    public Lab updateLabProfile(UUID labId, String name, String description, String phoneNumber, String address) {
        Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Lab với ID: " + labId));

        lab.setName(name);
        lab.setDescription(description);
        lab.setPhoneNumber(phoneNumber);
        lab.setAddress(address);

        return labRepository.save(lab);
    }

    /**
     * Cập nhật hoặc cấu hình quy tắc công suất tối đa theo ngày (max_daily_rolls)[cite: 3]
     */
    @Transactional
    public LabCapacityRule updateCapacityRule(UUID labId, int maxDailyRolls) {
        // Kiểm tra xem Lab có tồn tại không
        labRepository.findById(labId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Lab với ID: " + labId));

        LabCapacityRule capacityRule = labCapacityRuleRepository.findByLabId(labId)
                .orElse(LabCapacityRule.builder().labId(labId).build());

        capacityRule.setMaxDailyRolls(maxDailyRolls);
        return labCapacityRuleRepository.save(capacityRule);
    }
}