package com.analoghub.platform.filmlab.facade;

import com.analoghub.platform.filmlab.service.LabAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FilmLabFacadeImpl implements FilmLabFacade {

    private final LabAnalyticsService labAnalyticsService;

    @Override
    @Transactional
    public boolean reserveCapacity(UUID labId, String processType, int requestedRolls) {
        // TODO: Logic kiểm tra và giữ chỗ công suất lab theo ngày/processType
        // Tạm thời trả về true để thông suốt luồng đặt lịch
        return true;
    }

    @Override
    @Transactional
    public void releaseCapacity(UUID labId, String processType, int rollsToRelease) {
        // TODO: Logic hoàn trả lại công suất khi đơn hàng bị hủy
    }

    @Override
    @Transactional
    public void recordOrderMetrics(UUID labId, BigDecimal orderAmount) {
        // Gọi sang service analytics để ghi nhận doanh thu và số lượng đơn hàng hàng ngày
        labAnalyticsService.recordDailyMetrics(labId, orderAmount);
    }
}