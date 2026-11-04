package com.analoghub.platform.delivery.service;

import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import com.analoghub.platform.delivery.domain.DeliveryDirection;
import com.analoghub.platform.delivery.domain.DeliveryTask;
import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import com.analoghub.platform.delivery.event.DeliveryFailedEvent;
import com.analoghub.platform.delivery.event.FilmBagDeliveredToLabEvent;
import com.analoghub.platform.delivery.event.FilmBagPickedUpEvent;
import com.analoghub.platform.delivery.event.MarketParcelDeliveredEvent;
import com.analoghub.platform.delivery.event.NegativeReturnedToCustomerEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final StompNotificationPublisher stompPublisher;

    public String publishStatusChangedEvent(DeliveryTask task, String failureNote) {
        DeliveryTaskStatus status = task.getStatus();
        DeliveryDirection direction = task.getDirection();
        Instant now = Instant.now();

        if (status == DeliveryTaskStatus.PICKED_UP && direction == DeliveryDirection.PICKUP_TO_LAB) {
            UUID customerId = extractUuid(task.getPickupContactAddress(), "userId");
            UUID labId = extractUuid(task.getDropoffContactAddress(), "labId");
            eventPublisher.publishEvent(new FilmBagPickedUpEvent(
                    task.getId(),
                    task.getReferenceId(),
                    customerId,
                    labId,
                    now
            ));
            return "FilmBagPickedUpEvent";
        }

        if (status == DeliveryTaskStatus.DELIVERED && direction == DeliveryDirection.PICKUP_TO_LAB) {
            UUID labId = extractUuid(task.getDropoffContactAddress(), "labId");
            eventPublisher.publishEvent(new FilmBagDeliveredToLabEvent(
                    task.getId(),
                    task.getReferenceId(),
                    labId,
                    task.getProofOfDeliveryS3Key(),
                    now
            ));
            return "FilmBagDeliveredToLabEvent";
        }

        if (status == DeliveryTaskStatus.DELIVERED && direction == DeliveryDirection.RETURN_TO_CUSTOMER) {
            UUID customerId = extractUuid(task.getDropoffContactAddress(), "userId");
            eventPublisher.publishEvent(new NegativeReturnedToCustomerEvent(
                    task.getId(),
                    task.getReferenceId(),
                    customerId,
                    now
            ));
            return "NegativeReturnedToCustomerEvent";
        }

        if (status == DeliveryTaskStatus.DELIVERED && direction == DeliveryDirection.C2C_SHIPPING) {
            UUID sellerId = extractUuid(task.getPickupContactAddress(), "userId");
            UUID buyerId = extractUuid(task.getDropoffContactAddress(), "userId");
            eventPublisher.publishEvent(new MarketParcelDeliveredEvent(
                    task.getId(),
                    task.getReferenceId(),
                    buyerId,
                    sellerId,
                    now
            ));
            return "MarketParcelDeliveredEvent";
        }

        if (status == DeliveryTaskStatus.DELIVERY_FAILED || status == DeliveryTaskStatus.TIMEOUT_FAILED) {
            eventPublisher.publishEvent(new DeliveryFailedEvent(
                    task.getId(),
                    task.getReferenceType(),
                    task.getReferenceId(),
                    failureNote != null ? failureNote : "Delivery failed"
            ));
            return "DeliveryFailedEvent";
        }

        return null;
    }

    public void broadcastTaskLocation(UUID taskId, DeliveryTaskStatus status, Double latitude, Double longitude, Double heading, Double speedKmh) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("status", status != null ? status.name() : null);
        payload.put("latitude", latitude);
        payload.put("longitude", longitude);
        if (heading != null) {
            payload.put("headingDegrees", heading);
        }
        if (speedKmh != null) {
            payload.put("speedKmh", speedKmh);
        }
        payload.put("timestamp", Instant.now().toString());
        stompPublisher.publishDeliveryLocation(taskId, payload);
    }

    private UUID extractUuid(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        Object val = map.get(key);
        if (val instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(val.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
