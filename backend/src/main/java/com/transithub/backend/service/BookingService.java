package com.transithub.backend.service;

import com.transithub.backend.model.*;
import com.transithub.backend.repository.*;
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

        Booking booking = Booking.builder()
                .user(user)
                .schedule(schedule)
                .seatNumber(seatNumber)
                .totalAmount(schedule.getRoute().getBasePrice())
                .status("pending")
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