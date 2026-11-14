package com.analoghub.platform.filmlab.facade;

import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.repository.LabCapacityRuleRepository;
import com.analoghub.platform.filmlab.repository.ServicePackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FilmLabFacadeImpl implements FilmLabFacade {

    private final LabCapacityRuleRepository capacityRuleRepository;
    private final ServicePackageRepository servicePackageRepository;

    @Override
    @Transactional
    public void reserveCapacity(UUID labId, String processType, int rollCount) {
        LabCapacityRule rule = capacityRuleRepository.findByLabIdAndProcessType(labId, processType)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình công suất cho quy trình: " + processType));

        // Fix: Sử dụng isAcceptingOrders() chuẩn của Lombok sinh ra
        if (!Boolean.TRUE.equals(rule.isAcceptingOrders())) {
            throw new IllegalArgumentException("Lab hiện đang tạm ngưng nhận đơn cho quy trình " + processType);
        }

        rule.setCurrentQueuedRolls(rule.getCurrentQueuedRolls() + rollCount);
        capacityRuleRepository.save(rule);
    }

    @Override
    @Transactional
    public void releaseCapacity(UUID labId, String processType, int rollCount) {
        capacityRuleRepository.findByLabIdAndProcessType(labId, processType).ifPresent(rule -> {
            int updated = Math.max(0, rule.getCurrentQueuedRolls() - rollCount);
            rule.setCurrentQueuedRolls(updated);
            capacityRuleRepository.save(rule);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public int getEstimatedTurnaroundHours(UUID labId, String processType) {
        return capacityRuleRepository.findByLabIdAndProcessType(labId, processType)
                .map(rule -> {
                    int batches = rule.getMaxDailyRolls() > 0 ? (rule.getCurrentQueuedRolls() / rule.getMaxDailyRolls()) : 0;
                    return rule.getBaseTurnaroundHours() + (batches * rule.getPenaltyHoursPerBatch());
                })
                .orElse(24);
    }

    @Override
    @Transactional(readOnly = true)
    public ServicePackageInfo getServicePackageInfo(UUID packageId) {
        return servicePackageRepository.findById(packageId)
                .map(pkg -> new ServicePackageInfo(
                        pkg.getId(), pkg.getLabId(), pkg.getName(), pkg.getFilmFormat(),
                        pkg.getProcessType(), pkg.getScanResolution(), pkg.getUnitPriceVnd()
                ))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy gói dịch vụ: " + packageId));
    }
}