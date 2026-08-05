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
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;

    public AdminController(BookingRepository bookingRepository,
                          UserRepository userRepository,
                          RouteRepository routeRepository,
                          ScheduleRepository scheduleRepository,
                          PaymentRepository paymentRepository,
                          TicketRepository ticketRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.routeRepository = routeRepository;
        this.scheduleRepository = scheduleRepository;
        this.paymentRepository = paymentRepository;
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/bookings")
    public List<Map<String, Object>> allBookings() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Booking b : bookingRepository.findAll()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId().toString());
            // Account holder who paid, plus the actual traveller on this seat
            // (differs for group bookings where one account books several seats).
            m.put("passenger", b.getUser() != null ? b.getUser().getName() : "");
            m.put("passengerName", b.getPassengerName());
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

    /**
     * Admin-only hard delete of a booking (used to clean up test records).
     * Removes dependent payment/ticket rows first to satisfy the foreign keys.
     */
    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<?> deleteBooking(@PathVariable UUID id) {
        if (!bookingRepository.existsById(id)) return ResponseEntity.notFound().build();
        paymentRepository.findAll().stream()
                .filter(p -> p.getBooking() != null && id.equals(p.getBooking().getId()))
                .forEach(paymentRepository::delete);
        ticketRepository.findAll().stream()
                .filter(t -> t.getBooking() != null && id.equals(t.getBooking().getId()))
                .forEach(ticketRepository::delete);
        bookingRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    /**
     * Admin-only hard delete of a passenger account (used to clean up test
     * registrations). Removes the user's bookings (and their payment/ticket
     * rows) first to satisfy the foreign keys.
     */
    @DeleteMapping("/passengers/{id}")
    public ResponseEntity<?> deletePassenger(@PathVariable UUID id) {
        if (!userRepository.existsById(id)) return ResponseEntity.notFound().build();
        bookingRepository.findAll().stream()
                .filter(b -> b.getUser() != null && id.equals(b.getUser().getId()))
                .forEach(b -> {
                    UUID bid = b.getId();
                    paymentRepository.findAll().stream()
                            .filter(p -> p.getBooking() != null && bid.equals(p.getBooking().getId()))
                            .forEach(paymentRepository::delete);
                    ticketRepository.findAll().stream()
                            .filter(t -> t.getBooking() != null && bid.equals(t.getBooking().getId()))
                            .forEach(ticketRepository::delete);
                    bookingRepository.delete(b);
                });
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
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
            m.put("emgName", u.getEmgName());
            m.put("emgPhone", u.getEmgPhone());
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

        // Split bookings: schedule-linked ones count directly; demo/mock
        // bookings (no Schedule) are attributed to the closest matching schedule
        // below, so real app bookings still show up as load.
        Map<UUID, Long> soldPerSchedule = new HashMap<>();
        List<Booking> customBookings = new ArrayList<>();
        for (Booking b : bookingRepository.findAll()) {
            if ("cancelled".equalsIgnoreCase(b.getStatus())) continue;
            if (b.getSchedule() != null) {
                soldPerSchedule.merge(b.getSchedule().getId(), 1L, Long::sum);
            } else {
                customBookings.add(b);
            }
        }

        // Upcoming schedules only.
        List<Schedule> upcoming = new ArrayList<>();
        for (Schedule s : scheduleRepository.findAll()) {
            if (s.getDepartsAt() == null || s.getDepartsAt().toLocalDate().isBefore(today)) continue;
            upcoming.add(s);
        }

        // Index schedules by operator-brand + route + date, so each demo booking
        // can be assigned to the departure closest to its time on that trip.
        Map<String, List<Schedule>> byTrip = new HashMap<>();
        for (Schedule s : upcoming) {
            Route r = s.getRoute();
            if (r == null || r.getOperator() == null) continue;
            String key = opBrand(r.getOperator().getCompanyName()) + "|" + r.getOrigin() + "|"
                    + r.getDestination() + "|" + s.getDepartsAt().toLocalDate();
            byTrip.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        Map<UUID, Long> customPerSchedule = new HashMap<>();
        for (Booking b : customBookings) {
            int[] d = parseDeparts(b.getDepartsAt());
            if (d == null) continue;
            java.time.LocalDate date;
            try { date = java.time.LocalDate.of(d[0], d[1], d[2]); } catch (Exception e) { continue; }
            String key = opBrand(b.getOperator()) + "|" + b.getOrigin() + "|" + b.getDestination() + "|" + date;
            List<Schedule> group = byTrip.get(key);
            if (group == null || group.isEmpty()) continue;
            Schedule best = null; int bestDiff = Integer.MAX_VALUE;
            for (Schedule s : group) {
                int diff = Math.abs(s.getDepartsAt().getHour() - d[3]);
                if (diff < bestDiff) { bestDiff = diff; best = s; }
            }
            if (best != null) customPerSchedule.merge(best.getId(), 1L, Long::sum);
        }

        for (Schedule s : upcoming) {
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
            Integer capacity = s.getBus() != null ? s.getBus().getCapacity() : null;
            long sold = soldPerSchedule.getOrDefault(s.getId(), 0L)
                    + customPerSchedule.getOrDefault(s.getId(), 0L);
            m.put("capacity", capacity);
            m.put("seatsBooked", sold);
            m.put("plateNumber", s.getBus() != null ? s.getBus().getPlateNumber() : null);
            m.put("driverName", (s.getBus() != null && s.getBus().getDriver() != null)
                    ? s.getBus().getDriver().getName() : null);
            out.add(m);
        }
        out.sort((a, c) -> String.valueOf(a.get("departsAt")).compareTo(String.valueOf(c.get("departsAt"))));
        return out;
    }

    /** Leading brand word of an operator name — "VIP Jeoun Executive" and
     *  "VIP Jeoun" both reduce to "VIP" so demo bookings match seeded schedules. */
    private static String opBrand(String op) {
        if (op == null) return "";
        String t = op.trim();
        int sp = t.indexOf(' ');
        return (sp > 0 ? t.substring(0, sp) : t).toUpperCase();
    }

    /** Parse a booking's departsAt (display "Tue, 4 Aug 2026 06:00 AM" or ISO)
     *  to [year, month, day, hour24], or null if it can't be read. */
    private static int[] parseDeparts(String s) {
        if (s == null || s.isBlank()) return null;
        String low = s.toLowerCase();

        java.util.regex.Matcher iso = java.util.regex.Pattern
                .compile("(20\\d\\d)-(\\d{1,2})-(\\d{1,2})").matcher(low);
        int year, month, day;
        if (iso.find()) {
            year = Integer.parseInt(iso.group(1));
            month = Integer.parseInt(iso.group(2));
            day = Integer.parseInt(iso.group(3));
        } else {
            java.util.regex.Matcher ym = java.util.regex.Pattern.compile("\\b(20\\d\\d)\\b").matcher(low);
            year = ym.find() ? Integer.parseInt(ym.group(1)) : today().getYear();
            String[] months = {"jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"};
            month = -1;
            for (int i = 0; i < 12; i++) if (low.contains(months[i])) { month = i + 1; break; }
            String cleaned = low.replaceAll("\\b20\\d\\d\\b", " ")
                    .replaceAll("\\d{1,2}:\\d{2}\\s*(am|pm)?", " ");
            java.util.regex.Matcher dm = java.util.regex.Pattern.compile("\\b(\\d{1,2})\\b").matcher(cleaned);
            day = dm.find() ? Integer.parseInt(dm.group(1)) : -1;
        }
        if (month < 1 || month > 12 || day < 1 || day > 31) return null;

        int hour = 0;
        java.util.regex.Matcher tm = java.util.regex.Pattern
                .compile("(\\d{1,2}):(\\d{2})\\s*(am|pm)?").matcher(low);
        if (tm.find()) {
            hour = Integer.parseInt(tm.group(1));
            String ap = tm.group(3);
            if ("pm".equals(ap) && hour != 12) hour += 12;
            if ("am".equals(ap) && hour == 12) hour = 0;
        }
        return new int[]{year, month, day, hour};
    }

    private static java.time.LocalDate today() { return java.time.LocalDate.now(); }

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
