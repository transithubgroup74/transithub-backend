package com.transithub.backend.controller;

import com.transithub.backend.model.Booking;
import com.transithub.backend.model.Route;
import com.transithub.backend.model.Schedule;
import com.transithub.backend.model.User;
import com.transithub.backend.repository.*;
import com.transithub.backend.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Read endpoints that power the admin web dashboard. Open (no auth) for the
 * demo — server-side staff auth is a separate follow-on task.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final ScheduleRepository scheduleRepository;

    public AdminController(BookingRepository bookingRepository,
                          UserRepository userRepository,
                          RouteRepository routeRepository,
                          ScheduleRepository scheduleRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.routeRepository = routeRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @GetMapping("/bookings")
    public List<Map<String, Object>> allBookings() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Booking b : bookingRepository.findAll()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId().toString());
            m.put("passenger", b.getUser() != null ? b.getUser().getName() : "");
            m.put("email", b.getUser() != null ? b.getUser().getEmail() : "");
            m.put("route", BookingService.routeLabel(b));
            m.put("seat", b.getSeatNumber());
            m.put("amount", b.getTotalAmount());
            m.put("status", b.getStatus());
            m.put("busClass", b.getBusClass());
            m.put("operator", b.getOperator());
            m.put("departsAt", b.getDepartsAt());
            m.put("createdAt", b.getCreatedAt());
            m.put("qrCode", b.getQrCode());
            out.add(m);
        }
        out.sort((a, c) -> String.valueOf(c.get("createdAt")).compareTo(String.valueOf(a.get("createdAt"))));
        return out;
    }

    @GetMapping("/passengers")
    public List<Map<String, Object>> allPassengers() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Booking> bookings = bookingRepository.findAll();
        for (User u : userRepository.findAll()) {
            long trips = bookings.stream().filter(b -> b.getUser() != null && b.getUser().getId().equals(u.getId())).count();
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId().toString());
            m.put("name", u.getName());
            m.put("email", u.getEmail());
            m.put("phone", u.getPhone());
            m.put("createdAt", u.getCreatedAt());
            m.put("trips", trips);
            out.add(m);
        }
        return out;
    }

    /**
     * Flattened schedule list for the dashboard. The plain /api/schedules
     * returns ~1,200 fully-nested entities (~1 MB) which is slow to transfer;
     * this returns just the fields the dashboard renders so it loads fast.
     */
    @GetMapping("/schedules")
    public List<Map<String, Object>> schedules() {
        List<Map<String, Object>> out = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (Schedule s : scheduleRepository.findAll()) {
            // Upcoming only — keep all of today plus future days, hide past ones
            // so the timetable is forward-looking (soonest first once sorted).
            if (s.getDepartsAt() == null || s.getDepartsAt().toLocalDate().isBefore(today)) continue;
            Route r = s.getRoute();
            String model = s.getBus() != null ? s.getBus().getModel() : null;
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId().toString());
            m.put("origin", r != null ? r.getOrigin() : null);
            m.put("destination", r != null ? r.getDestination() : null);
            m.put("operator", (r != null && r.getOperator() != null) ? r.getOperator().getCompanyName() : null);
            m.put("departsAt", s.getDepartsAt());
            m.put("status", s.getStatus());
            m.put("source", s.getSource());
            m.put("busClass", (model != null && model.toLowerCase().contains("exec")) ? "Executive" : "Regular");
            out.add(m);
        }
        out.sort((a, c) -> String.valueOf(a.get("departsAt")).compareTo(String.valueOf(c.get("departsAt"))));
        return out;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<Booking> bookings = bookingRepository.findAll();
        long confirmed = bookings.stream().filter(b -> "confirmed".equals(b.getStatus())).count();
        long completed = bookings.stream().filter(b -> "completed".equals(b.getStatus())).count();
        long cancelled = bookings.stream().filter(b -> "cancelled".equals(b.getStatus())).count();
        long pending = bookings.stream().filter(b -> "pending".equals(b.getStatus())).count();
        BigDecimal revenue = bookings.stream()
                .filter(b -> "confirmed".equals(b.getStatus()) || "completed".equals(b.getStatus()))
                .map(b -> b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new HashMap<>();
        m.put("totalBookings", bookings.size());
        m.put("confirmedBookings", confirmed);
        m.put("completedBookings", completed);
        m.put("cancelledBookings", cancelled);
        m.put("pendingBookings", pending);
        m.put("totalRevenue", revenue);
        m.put("totalPassengers", userRepository.count());
        m.put("totalRoutes", routeRepository.count());
        m.put("totalSchedules", scheduleRepository.count());
        return m;
    }

    @GetMapping("/revenue")
    public Map<String, Object> revenue() {
        List<Booking> paid = bookingRepository.findAll().stream()
                .filter(b -> "confirmed".equals(b.getStatus()) || "completed".equals(b.getStatus()))
                .toList();
        BigDecimal total = paid.stream()
                .map(b -> b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = paid.isEmpty() ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(paid.size()), 2, java.math.RoundingMode.HALF_UP);
        Map<String, Object> m = new HashMap<>();
        m.put("total", total);
        m.put("paidCount", paid.size());
        m.put("avgPerBooking", avg);
        return m;
    }
}
