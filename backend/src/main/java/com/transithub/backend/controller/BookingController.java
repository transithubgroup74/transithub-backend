package com.transithub.backend.controller;

import com.transithub.backend.model.Booking;
import com.transithub.backend.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final com.transithub.backend.service.EmailService emailService;

    public BookingController(BookingService bookingService, com.transithub.backend.service.EmailService emailService) {
        this.bookingService = bookingService;
        this.emailService = emailService;
    }

    @PostMapping
    public ResponseEntity<Booking> createBooking(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String email = authentication.getName();
        UUID scheduleId = UUID.fromString((String) body.get("scheduleId"));
        Integer seatNumber = (Integer) body.get("seatNumber");
        String qrCode = (String) body.get("qrCode");
        return ResponseEntity.ok(bookingService.createBooking(email, scheduleId, seatNumber, qrCode));
    }

    // Create a booking from full trip details (works for demo/mock buses that
    // have no real schedule). Persists to the user's account so it syncs.
    @PostMapping("/custom")
    public ResponseEntity<?> createCustomBooking(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            Integer seat = body.get("seatNumber") != null
                    ? Integer.valueOf(String.valueOf(body.get("seatNumber"))) : null;
            java.math.BigDecimal amount = body.get("totalAmount") != null
                    ? new java.math.BigDecimal(String.valueOf(body.get("totalAmount"))) : java.math.BigDecimal.ZERO;
            Booking booking = bookingService.createCustomBooking(
                    email,
                    (String) body.get("origin"),
                    (String) body.get("destination"),
                    seat,
                    amount,
                    (String) body.get("departsAt"),
                    (String) body.get("operator"),
                    (String) body.get("busClass"),
                    (String) body.get("qrCode"),
                    (String) body.get("status"));
            return ResponseEntity.ok(booking);
        } catch (com.transithub.backend.exception.ApiException e) {
            throw e;  // let the global handler return its status + code (e.g. seat_taken)
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Seats already taken on a demo/mock trip, so the picker can grey them out
    // for mock buses too (which have no real schedule).
    // e.g. /api/bookings/custom-seats?operator=VIP+Jeoun&origin=Kumasi&destination=Accra&departsAt=...
    @GetMapping("/custom-seats")
    public ResponseEntity<List<Integer>> customBookedSeats(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String departsAt) {
        return ResponseEntity.ok(bookingService.getCustomBookedSeats(operator, origin, destination, departsAt));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Booking>> getMyBookings(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(bookingService.getUserBookings(email));
    }

    // Seat numbers already taken on a schedule (so the seat picker greys them
    // out). Excludes cancelled bookings.
    @GetMapping("/seats/{scheduleId}")
    public ResponseEntity<List<Integer>> bookedSeats(@PathVariable UUID scheduleId) {
        List<Integer> seats = bookingService.getBookedSeats(scheduleId);
        return ResponseEntity.ok(seats);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBookingById(@PathVariable UUID id, Authentication authentication) {
        return bookingService.getBookingById(id, authentication.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/receipt")
    public ResponseEntity<?> sendReceipt(@PathVariable UUID id, Authentication authentication) {
        return bookingService.getBookingById(id, authentication.getName())
                .map(booking -> { emailService.sendReceipt(booking); return ResponseEntity.ok(Map.of("sent", true)); })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable UUID id, Authentication authentication) {
        try {
            return ResponseEntity.ok(bookingService.cancelBooking(id, authentication.getName()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> completeBooking(@PathVariable UUID id) {
        try {
            Booking booking = bookingService.completeBooking(id);
            return ResponseEntity.ok(Map.of(
                "status", "completed",
                "bookingId", booking.getId().toString(),
                "passenger", booking.getUser().getName(),
                "seat", booking.getSeatNumber(),
                "route", BookingService.routeLabel(booking)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/verify")
    public ResponseEntity<?> verifyBooking(@PathVariable UUID id) {
        return bookingService.getBookingByIdForConductor(id)
                .map(b -> ResponseEntity.ok(Map.of(
                    "id", b.getId().toString(),
                    "status", b.getStatus(),
                    "passenger", b.getUser().getName(),
                    "seat", b.getSeatNumber(),
                    "route", BookingService.routeLabel(b)
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/verify-qr")
    public ResponseEntity<?> verifyByQr(@RequestBody Map<String, String> body) {
        String qrCode = body.get("qrCode");
        if (qrCode == null || qrCode.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "qrCode is required"));
        }
        return bookingService.findByQrCode(qrCode)
                .map(b -> ResponseEntity.ok(Map.of(
                    "id", b.getId().toString(),
                    "status", b.getStatus(),
                    "passenger", b.getUser().getName(),
                    "seat", b.getSeatNumber(),
                    "route", BookingService.routeLabel(b)
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}