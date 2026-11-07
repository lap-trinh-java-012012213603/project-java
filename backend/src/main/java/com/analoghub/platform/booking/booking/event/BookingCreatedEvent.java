package com.analoghub.platform.booking.event;

import java.util.UUID;

public record BookingCreatedEvent(
        UUID bookingId,
        UUID labId
) {}