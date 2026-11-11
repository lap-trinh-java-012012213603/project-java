package com.analoghub.platform.booking.controller;

import com.analoghub.platform.booking.dto.BookingQuoteRequest;
import com.analoghub.platform.booking.dto.BookingQuoteResponse;
import com.analoghub.platform.booking.service.BookingQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingQuoteController {

    private final BookingQuoteService bookingQuoteService;

    @PostMapping("/quote")
    public ResponseEntity<BookingQuoteResponse> getBookingQuote(@RequestBody BookingQuoteRequest request) {
        BookingQuoteResponse response = bookingQuoteService.calculateQuote(request);
        return ResponseEntity.ok(response);
    }
}