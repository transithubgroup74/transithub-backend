package com.transithub.backend.controller;

import com.transithub.backend.model.Bus;
import com.transithub.backend.model.Route;
import com.transithub.backend.model.Schedule;
import com.transithub.backend.repository.BusRepository;
import com.transithub.backend.repository.RouteRepository;
import com.transithub.backend.repository.ScheduleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleRepository scheduleRepository;
    private final RouteRepository routeRepository;
    private final BusRepository busRepository;

    public ScheduleController(ScheduleRepository scheduleRepository,
                              RouteRepository routeRepository,
                              BusRepository busRepository) {
        this.scheduleRepository = scheduleRepository;
        this.routeRepository = routeRepository;
        this.busRepository = busRepository;
    }

    // Admin: create a schedule. Body: { routeId, departsAt, busId? }
    // departsAt accepts "2026-07-01T06:00" or "2026-07-01 06:00".
    @PostMapping
    public ResponseEntity<?> createSchedule(@RequestBody Map<String, Object> body) {
        try {
            Route route = routeRepository.findById(UUID.fromString(String.valueOf(body.get("routeId"))))
                    .orElseThrow(() -> new RuntimeException("Route not found"));
            Bus bus = null;
            Object busId = body.get("busId");
            if (busId != null) bus = busRepository.findById(UUID.fromString(String.valueOf(busId))).orElse(null);
            // Prefer a bus that belongs to the route's operator so the schedule
            // shows the right company; fall back to any bus.
            if (bus == null && route.getOperator() != null) {
                bus = busRepository.findAll().stream()
                        .filter(b -> b.getOperator() != null && route.getOperator().getId().equals(b.getOperator().getId()))
                        .findFirst().orElse(null);
            }
            if (bus == null) bus = busRepository.findAll().stream().findFirst().orElse(null);

            String raw = String.valueOf(body.get("departsAt")).replace(' ', 'T');
            LocalDateTime departsAt = LocalDateTime.parse(raw);

            Schedule schedule = Schedule.builder()
                    .route(route)
                    .bus(bus)
                    .departsAt(departsAt)
                    .status("active")
                    .source("admin")
                    .build();
            return ResponseEntity.ok(scheduleRepository.save(schedule));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSchedule(@PathVariable UUID id) {
        if (!scheduleRepository.existsById(id)) return ResponseEntity.notFound().build();
        scheduleRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping
    public ResponseEntity<List<Schedule>> getAllSchedules() {
        return ResponseEntity.ok(scheduleRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Schedule> getScheduleById(@PathVariable UUID id) {
        return scheduleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Search by routeId + date: /api/schedules/search?routeId=xxx&date=2025-06-21
    @GetMapping("/search")
    public ResponseEntity<List<Schedule>> search(
            @RequestParam(required = false) UUID routeId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination) {

        if (origin != null && destination != null) {
            LocalDate d = date != null ? LocalDate.parse(date) : LocalDate.now();
            List<Schedule> results = scheduleRepository.findByOriginDestinationAndDate(
                    origin, destination, d.atStartOfDay(), d.plusDays(1).atStartOfDay());
            return ResponseEntity.ok(results);
        }

        if (routeId != null) {
            LocalDate d = date != null ? LocalDate.parse(date) : LocalDate.now();
            List<Schedule> results = scheduleRepository.findByRouteAndDate(
                    routeId, d.atStartOfDay(), d.plusDays(1).atStartOfDay());
            return ResponseEntity.ok(results);
        }

        return ResponseEntity.ok(scheduleRepository.findAll());
    }
}
