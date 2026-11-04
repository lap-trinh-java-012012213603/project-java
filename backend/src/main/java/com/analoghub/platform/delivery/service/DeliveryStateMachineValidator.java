package com.analoghub.platform.delivery.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class DeliveryStateMachineValidator {

    private static final Map<DeliveryTaskStatus, Set<DeliveryTaskStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(DeliveryTaskStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(DeliveryTaskStatus.CREATED, EnumSet.of(
                DeliveryTaskStatus.ASSIGNING_DRIVER,
                DeliveryTaskStatus.CANCELLED,
                DeliveryTaskStatus.DLV_CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(DeliveryTaskStatus.ASSIGNING_DRIVER, EnumSet.of(
                DeliveryTaskStatus.DRIVER_ASSIGNED,
                DeliveryTaskStatus.TIMEOUT_FAILED,
                DeliveryTaskStatus.CANCELLED,
                DeliveryTaskStatus.DLV_CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(DeliveryTaskStatus.TIMEOUT_FAILED, EnumSet.of(
                DeliveryTaskStatus.ASSIGNING_DRIVER,
                DeliveryTaskStatus.CANCELLED,
                DeliveryTaskStatus.DLV_CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(DeliveryTaskStatus.DRIVER_ASSIGNED, EnumSet.of(
                DeliveryTaskStatus.PICKED_UP,
                DeliveryTaskStatus.DELIVERY_FAILED,
                DeliveryTaskStatus.CANCELLED,
                DeliveryTaskStatus.DLV_CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(DeliveryTaskStatus.PICKED_UP, EnumSet.of(
                DeliveryTaskStatus.IN_TRANSIT,
                DeliveryTaskStatus.DELIVERED,
                DeliveryTaskStatus.DELIVERY_FAILED
        ));
        ALLOWED_TRANSITIONS.put(DeliveryTaskStatus.IN_TRANSIT, EnumSet.of(
                DeliveryTaskStatus.DELIVERED,
                DeliveryTaskStatus.DELIVERY_FAILED
        ));
        ALLOWED_TRANSITIONS.put(DeliveryTaskStatus.DELIVERY_FAILED, EnumSet.of(
                DeliveryTaskStatus.RETURNED_TO_SENDER
        ));
    }

    public void validateTransition(DeliveryTaskStatus from, DeliveryTaskStatus to) {
        if (from == to) {
            return;
        }
        Set<DeliveryTaskStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(DeliveryTaskStatus.class));
        if (!allowed.contains(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Invalid delivery task status transition from " + from + " to " + to);
        }
    }
}
