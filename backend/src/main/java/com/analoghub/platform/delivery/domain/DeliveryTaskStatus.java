package com.analoghub.platform.delivery.domain;

public enum DeliveryTaskStatus {
    CREATED,
    ASSIGNING_DRIVER,
    DRIVER_ASSIGNED,
    TIMEOUT_FAILED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    DELIVERY_FAILED,
    RETURNED_TO_SENDER,
    CANCELLED,
    DLV_CANCELLED
}
