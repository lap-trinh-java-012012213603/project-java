package com.analogohub.platform.booking.event;

import java.util.UUID;

public record BookingCreatedEvent(
        UUID bookingId,
        UUID labId
) {}