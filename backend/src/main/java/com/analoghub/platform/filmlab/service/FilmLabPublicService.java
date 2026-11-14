package com.analoghub.platform.filmlab.service;

import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.dto.LabDetailResponse;
import com.analoghub.platform.filmlab.event.LabViewedEvent;
import com.analoghub.platform.filmlab.repository.LabCapacityRuleRepository;
import com.analoghub.platform.filmlab.repository.LabRepository;
import com.analoghub.platform.filmlab.repository.ServicePackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FilmLabPublicService {

    private final LabRepository labRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final LabCapacityRuleRepository labCapacityRuleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    @Transactional(readOnly = true)
    public LabDetailResponse getLabDetails(String slugOrId, String viewerIpOrId) {
        Lab lab;
        try {
            UUID labId = UUID.fromString(slugOrId);
            lab = labRepository.findById(labId)
                    // Sửa lại đoạn này, chọn ErrorCode cho phù hợp với project của bạn
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        } catch (IllegalArgumentException e) {
            lab = labRepository.findBySlug(slugOrId)
                    // Tương tự ở đây
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        }

        publishViewEventWithDebounce(lab.getId(), viewerIpOrId);

        return LabDetailResponse.builder()
                .lab(lab)
                .packages(servicePackageRepository.findByLabIdAndIsActiveTrue(lab.getId()))
                .capacities(labCapacityRuleRepository.findByLabId(lab.getId()))
                .build();
    }

    private void publishViewEventWithDebounce(UUID labId, String viewerIpOrId) {
        String redisKey = "lab_view:" + labId + ":" + viewerIpOrId;
        // Debounce 5 phút bằng Redis để tránh spam view
        Boolean isNewView = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", Duration.ofMinutes(5));

        if (Boolean.TRUE.equals(isNewView)) {
            eventPublisher.publishEvent(new LabViewedEvent(labId, viewerIpOrId));
        }
    }
}