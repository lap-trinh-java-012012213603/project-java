package com.analoghub.platform.booking.service;

import com.analoghub.platform.booking.dto.BookingQuoteRequest;
import com.analoghub.platform.booking.dto.BookingQuoteResponse;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class BookingQuoteService {

    private final FilmLabFacade filmLabFacade;
    // private final DeliveryFacade deliveryFacade; // Uncomment nếu đã có DeliveryFacade trong project

    public BookingQuoteResponse calculateQuote(BookingQuoteRequest request) {
        // 1. Tạm tính tiền dịch vụ (Ở đây sẽ gọi FilmLabFacade hoặc Repository để lấy snapshot giá các gói dịch vụ)
        BigDecimal serviceSubtotal = BigDecimal.valueOf(150000); // Giá ví dụ tạm tính

        // 2. Tính phí ship qua DeliveryFacade (ví dụ gọi estimateShippingFee)
        BigDecimal shippingFee = request.isIncludeReturnShipping()
                ? BigDecimal.valueOf(30000)
                : BigDecimal.valueOf(15000);

        // 3. Tổng tiền
        BigDecimal totalAmount = serviceSubtotal.add(shippingFee);

        // 4. Tính thời gian trả ảnh dự kiến dựa trên SLA của lab
        Instant estimatedTime = Instant.now().plus(48, ChronoUnit.HOURS); // Ví dụ SLA 48 tiếng

        return BookingQuoteResponse.builder()
                .serviceSubtotalVnd(serviceSubtotal)
                .shippingFeeVnd(shippingFee)
                .totalAmountVnd(totalAmount)
                .estimatedCompletionTime(estimatedTime)
                .build();
    }
}