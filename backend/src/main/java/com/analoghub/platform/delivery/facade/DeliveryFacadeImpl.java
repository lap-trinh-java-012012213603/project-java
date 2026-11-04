package com.analoghub.platform.delivery.facade;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.delivery.domain.DeliveryDirection;
import com.analoghub.platform.delivery.domain.DeliveryTask;
import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import com.analoghub.platform.delivery.domain.DeliveryTrackingLog;
import com.analoghub.platform.delivery.domain.LogisticsProviderType;
import com.analoghub.platform.delivery.dto.ContactAddressDto;
import com.analoghub.platform.delivery.dto.CreateDeliveryTaskRequest;
import com.analoghub.platform.delivery.dto.ShippingFeeEstimateDto;
import com.analoghub.platform.delivery.dto.ShippingFeeEstimateRequest;
import com.analoghub.platform.delivery.provider.LogisticsProvider;
import com.analoghub.platform.delivery.provider.LogisticsProviderRegistry;
import com.analoghub.platform.delivery.provider.ProviderDispatchResult;
import com.analoghub.platform.delivery.repository.DeliveryTaskRepository;
import com.analoghub.platform.delivery.repository.DeliveryTrackingLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryFacadeImpl implements DeliveryFacade {

    private static final Set<DeliveryTaskStatus> CANCELLABLE_STATUSES = EnumSet.of(
            DeliveryTaskStatus.CREATED,
            DeliveryTaskStatus.ASSIGNING_DRIVER,
            DeliveryTaskStatus.DRIVER_ASSIGNED
    );

    private final DeliveryTaskRepository taskRepository;
    private final DeliveryTrackingLogRepository trackingLogRepository;
    private final LogisticsProviderRegistry providerRegistry;

    @Override
    @Transactional
    public UUID createDeliveryTask(CreateDeliveryTaskRequest request) {
        if (request.pickupAddress() == null || request.dropoffAddress() == null) {
            throw new BusinessException(ErrorCode.DELIVERY_ADDRESS_REQUIRED);
        }

        DeliveryDirection direction = parseDirection(request.direction());
        LogisticsProvider provider = providerRegistry.getProvider(request.preferredProvider());

        double distanceKm = LogisticsProviderRegistry.estimateRoadDistanceKm(
                request.pickupAddress().latitude(),
                request.pickupAddress().longitude(),
                request.dropoffAddress().latitude(),
                request.dropoffAddress().longitude()
        );
        BigDecimal feeVnd = provider.estimateFee(distanceKm, false);
        Instant deadline = request.pickupDeadlineAt() != null
                ? request.pickupDeadlineAt()
                : Instant.now().plus(Duration.ofMinutes(30));

        DeliveryTask task = DeliveryTask.builder()
                .referenceType(request.referenceType())
                .referenceId(request.referenceId())
                .direction(direction)
                .provider(provider.providerType())
                .pickupContactAddress(toAddressMap(request.pickupAddress()))
                .dropoffContactAddress(toAddressMap(request.dropoffAddress()))
                .shippingFeeVnd(feeVnd)
                .status(DeliveryTaskStatus.CREATED)
                .pickupDeadlineAt(deadline)
                .build();

        ProviderDispatchResult dispatchResult = provider.dispatch(task);
        task.setExternalTrackingId(dispatchResult.externalTrackingId());
        task.setStatus(dispatchResult.initialStatus());
        if (dispatchResult.estimatedFeeVnd() != null) {
            task.setShippingFeeVnd(dispatchResult.estimatedFeeVnd());
        }

        DeliveryTask saved = taskRepository.save(task);

        trackingLogRepository.save(DeliveryTrackingLog.builder()
                .deliveryTaskId(saved.getId())
                .status(saved.getStatus())
                .latitude(request.pickupAddress().latitude())
                .longitude(request.pickupAddress().longitude())
                .note("Delivery task created via " + saved.getProvider())
                .loggedAt(Instant.now())
                .build());

        return saved.getId();
    }

    @Override
    @Transactional
    public void cancelDeliveryTask(UUID taskId, String reason) {
        DeliveryTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Delivery task not found"));

        if (!CANCELLABLE_STATUSES.contains(task.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Delivery task cannot be cancelled in status " + task.getStatus());
        }

        LogisticsProvider provider = providerRegistry.getProvider(task.getProvider());
        provider.cancel(task, reason);

        task.setStatus(DeliveryTaskStatus.DLV_CANCELLED);
        taskRepository.save(task);

        trackingLogRepository.save(DeliveryTrackingLog.builder()
                .deliveryTaskId(task.getId())
                .status(DeliveryTaskStatus.DLV_CANCELLED)
                .note(reason != null ? reason : "Cancelled")
                .loggedAt(Instant.now())
                .build());
    }

    @Override
    public ShippingFeeEstimateDto estimateShippingFee(ShippingFeeEstimateRequest request) {
        double distanceKm = LogisticsProviderRegistry.estimateRoadDistanceKm(
                request.pickupLat(),
                request.pickupLon(),
                request.dropoffLat(),
                request.dropoffLon()
        );
        LogisticsProvider provider = providerRegistry.getProvider(LogisticsProviderType.INTERNAL_DRIVER);
        BigDecimal feeVnd = provider.estimateFee(distanceKm, request.twoWay());
        return new ShippingFeeEstimateDto(feeVnd, distanceKm, provider.providerType().name());
    }

    private DeliveryDirection parseDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return DeliveryDirection.PICKUP_TO_LAB;
        }
        try {
            return DeliveryDirection.valueOf(direction.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DeliveryDirection.PICKUP_TO_LAB;
        }
    }

    private Map<String, Object> toAddressMap(ContactAddressDto dto) {
        Map<String, Object> map = new HashMap<>();
        map.put("contactName", dto.contactName());
        map.put("contactPhone", dto.contactPhone());
        map.put("fullAddress", dto.fullAddress());
        map.put("latitude", dto.latitude());
        map.put("longitude", dto.longitude());
        return map;
    }
}
