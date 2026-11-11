package com.analoghub.platform.booking.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class OrderItemQuoteDto {
    private UUID servicePackageId;
    private int quantity;
}