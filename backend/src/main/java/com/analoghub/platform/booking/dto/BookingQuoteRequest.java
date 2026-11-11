package com.analoghub.platform.booking.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class BookingQuoteRequest {
    private UUID labId;
    private List<OrderItemQuoteDto> items;
    private boolean includeReturnShipping;
    private String deliveryAddress;
}