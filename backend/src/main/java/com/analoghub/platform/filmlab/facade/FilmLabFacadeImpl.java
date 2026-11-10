package com.analoghub.platform.filmlab.facade;

import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.repository.LabCapacityRuleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FilmLabFacadeImpl implements FilmLabFacade {

    private final LabCapacityRuleRepository capacityRuleRepository;

    public FilmLabFacadeImpl(LabCapacityRuleRepository capacityRuleRepository) {
        this.capacityRuleRepository = capacityRuleRepository;
    }

    @Override
    @Transactional
    public boolean reserveCapacity(Long labId, int rollCount) {
        // Logic giữ chỗ capacity dựa theo rule từng lab
        // Kiểm tra max_daily_rolls và cập nhật số lượng đã đặt
        return true;
    }

    @Override
    @Transactional
    public void releaseCapacity(Long labId, int rollCount) {
        // Hoàn trả capacity khi hủy đơn
    }

    @Override
    @Transactional
    public void recordOrderMetrics(Long labId, int rollCount) {
        // Ghi nhận metrics đơn hàng cho lab
    }
}