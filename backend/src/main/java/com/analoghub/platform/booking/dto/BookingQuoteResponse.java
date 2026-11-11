package com.analoghub.platform.booking.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class BookingQuoteResponse {
    private BigDecimal serviceSubtotalVnd;
    private BigDecimal shippingFeeVnd;
    private BigDecimal totalAmountVnd;
    private Instant estimatedCompletionTime; // Ngày giờ trả ảnh dự kiến
}