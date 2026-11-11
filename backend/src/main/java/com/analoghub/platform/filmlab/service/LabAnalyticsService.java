package com.analoghub.platform.filmlab.service;

import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.repository.LabCapacityRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID; // Thêm import UUID
import java.math.BigDecimal;

@Service
public class LabAnalyticsService {

    private final LabCapacityRuleRepository capacityRuleRepository;

    public LabAnalyticsService(LabCapacityRuleRepository capacityRuleRepository) {
        this.capacityRuleRepository = capacityRuleRepository;
    }

    /**
     * Lấy thống kê phân tích hoạt động hàng ngày của Lab
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDailyAnalytics(UUID labId, LocalDate targetDate) {
        Map<String, Object> metrics = new HashMap<>();

        Optional<LabCapacityRule> ruleOpt = capacityRuleRepository.findById(labId);

        int maxRolls = ruleOpt.map(LabCapacityRule::getMaxDailyRolls).orElse(0);

        metrics.put("labId", labId);
        metrics.put("targetDate", targetDate);
        metrics.put("maxDailyCapacity", maxRolls);
        metrics.put("capacityUtilizationRate", 0.0);

        return metrics;
    }

    /**
     * Thống kê công suất hiện tại của Lab
     */
    @Transactional(readOnly = true)
    public double calculateCapacityUtilization(UUID labId, int currentBookedRolls) {
        LabCapacityRule rule = capacityRuleRepository.findById(labId)
                .orElseThrow(() -> new IllegalArgumentException("Lab capacity rule not found for id: " + labId));

        if (rule.getMaxDailyRolls() <= 0) {
            return 0.0;
        }
        return (double) currentBookedRolls / rule.getMaxDailyRolls() * 100.0;
    }
    public void recordDailyMetrics(UUID labId, BigDecimal orderAmount) {
        // TODO: Logic ghi nhận doanh thu và số lượng đơn hàng vào bảng LabDailyAnalytics theo ngày
    }
}
