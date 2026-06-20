package com.transithub.backend.controller;

import com.transithub.backend.model.Booking;
import com.transithub.backend.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<Booking> createBooking(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String email = authentication.getName();
        UUID scheduleId = UUID.fromString((String) body.get("scheduleId"));
        Integer seatNumber = (Integer) body.get("seatNumber");
        return ResponseEntity.ok(bookingService.createBooking(email, scheduleId, seatNumber));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Booking>> getMyBookings(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(bookingService.getUserBookings(email));
    }
}