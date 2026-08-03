package com.transithub.backend.service;

import com.transithub.backend.model.*;
import com.transithub.backend.repository.*;
import com.transithub.backend.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository,
                          ScheduleRepository scheduleRepository,
                          UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Booking createBooking(String userEmail, UUID scheduleId, Integer seatNumber) {
        return createBooking(userEmail, scheduleId, seatNumber, null);
    }

    @Transactional
    public Booking createBooking(String userEmail, UUID scheduleId, Integer seatNumber, String qrCode) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));

        // Reject a seat that's already taken on this bus (real concurrency guard
        // — two passengers can't hold the same seat).
        if (seatNumber != null && getBookedSeats(scheduleId).contains(seatNumber)) {
            throw new ApiException(409, "seat_taken",
                    "Seat " + seatNumber + " has just been taken. Please choose another seat.");
        }

        Booking booking = Booking.builder()
                .user(user)
                .schedule(schedule)
                .seatNumber(seatNumber)
                .totalAmount(schedule.getRoute().getBasePrice())
                // The app simulates instant successful payment before creating
                // the booking, so real-schedule bookings (e.g. admin-added
                // schedules) are confirmed on creation — consistent with the
                // custom/mock path. Revert to "pending" only if a real
                // awaiting-payment flow (live Paystack) is added later.
                .status("confirmed")
                .qrCode(qrCode)
                .origin(schedule.getRoute().getOrigin())
                .destination(schedule.getRoute().getDestination())
                .build();

        return bookingRepository.save(booking);
    }

    /**
     * Creates a booking from full trip details, without requiring a real
     * Schedule. Used for demo/mock buses so every booking still persists to
     * the user's account and syncs across devices.
     */
    @Transactional
    public Booking createCustomBooking(String userEmail, String origin, String destination,
                                       Integer seatNumber, java.math.BigDecimal totalAmount,
                                       String departsAt, String operator, String busClass,
                                       String qrCode, String status) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Same seat guard for demo/mock buses (no Schedule) — a trip instance is
        // operator + route + departure time.
        if (seatNumber != null
                && getCustomBookedSeats(operator, origin, destination, departsAt).contains(seatNumber)) {
            throw new ApiException(409, "seat_taken",
                    "Seat " + seatNumber + " has just been taken. Please choose another seat.");
        }

        Booking booking = Booking.builder()
                .user(user)
                .seatNumber(seatNumber)
                .totalAmount(totalAmount)
                .status(status != null ? status : "confirmed")
                .qrCode(qrCode)
                .origin(origin)
                .destination(destination)
                .departsAt(departsAt)
                .operator(operator)
                .busClass(busClass)
                .build();

        return bookingRepository.save(booking);
    }

    /** Route label that works whether the booking has a Schedule or its own fields. */
    public static String routeLabel(Booking b) {
        if (b.getSchedule() != null && b.getSchedule().getRoute() != null) {
            return b.getSchedule().getRoute().getOrigin() + " → " + b.getSchedule().getRoute().getDestination();
        }
        String o = b.getOrigin() != null ? b.getOrigin() : "";
        String d = b.getDestination() != null ? b.getDestination() : "";
        return o + " → " + d;
    }

    public List<Booking> getUserBookings(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return bookingRepository.findByUser(user);
    }

    // Seat numbers already taken on a schedule (excludes cancelled), so two
    // passengers can't book the same seat on the same bus.
    public List<Integer> getBookedSeats(UUID scheduleId) {
        return bookingRepository.findBySchedule_Id(scheduleId).stream()
                .filter(b -> !"cancelled".equalsIgnoreCase(b.getStatus()))
                .map(Booking::getSeatNumber)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    // Seats already taken on a demo/mock trip (identified by operator + route +
    // departure), so those seats show as unavailable to other passengers too.
    public List<Integer> getCustomBookedSeats(String operator, String origin, String destination, String departsAt) {
        if (operator == null || departsAt == null) return List.of();
        return bookingRepository
                .findByOperatorAndOriginAndDestinationAndDepartsAt(operator, origin, destination, departsAt)
                .stream()
                .filter(b -> !"cancelled".equalsIgnoreCase(b.getStatus()))
                .map(Booking::getSeatNumber)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    public Optional<Booking> getBookingById(UUID id, String userEmail) {
        return bookingRepository.findById(id)
                .filter(b -> b.getUser().getEmail().equals(userEmail));
    }

    @Transactional
    public Booking cancelBooking(UUID id, String userEmail) {
        Booking booking = bookingRepository.findById(id)
                .filter(b -> b.getUser().getEmail().equals(userEmail))
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        if ("cancelled".equals(booking.getStatus())) {
            throw new RuntimeException("Booking already cancelled");
        }
        booking.setStatus("cancelled");
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking completeBooking(UUID id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        if ("cancelled".equals(booking.getStatus())) {
            throw new RuntimeException("Booking is cancelled");
        }
        if ("completed".equals(booking.getStatus())) {
            throw new RuntimeException("Booking already completed");
        }
        booking.setStatus("completed");
        return bookingRepository.save(booking);
    }

    public Optional<Booking> getBookingByIdForConductor(UUID id) {
        return bookingRepository.findById(id);
    }

    public Optional<Booking> findByQrCode(String qrCode) {
        return bookingRepository.findByQrCode(qrCode);
    }
}