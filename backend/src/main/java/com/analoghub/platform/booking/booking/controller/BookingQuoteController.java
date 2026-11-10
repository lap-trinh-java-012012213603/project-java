package com.analoghub.platform.booking.controller;

import com.analoghub.platform.booking.service.BookingQuoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingQuoteController {

    private final BookingQuoteService bookingQuoteService;

    public BookingQuoteController(BookingQuoteService bookingQuoteService) {
        this.bookingQuoteService = bookingQuoteService;
    }

    @PostMapping("/quote")
    public ResponseEntity<BookingQuoteService.QuoteResponse> getBookingQuote(
            @RequestBody BookingQuoteService.QuoteRequest request) {
        BookingQuoteService.QuoteResponse response = bookingQuoteService.calculateQuote(request);
        return ResponseEntity.ok(response);
    }
}