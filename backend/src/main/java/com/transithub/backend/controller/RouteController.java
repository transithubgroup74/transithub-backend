package com.transithub.backend.controller;

import com.transithub.backend.model.Operator;
import com.transithub.backend.model.Route;
import com.transithub.backend.model.Schedule;
import com.transithub.backend.repository.BookingRepository;
import com.transithub.backend.repository.OperatorRepository;
import com.transithub.backend.repository.RouteRepository;
import com.transithub.backend.repository.ScheduleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteRepository routeRepository;
    private final OperatorRepository operatorRepository;
    private final ScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;

    public RouteController(RouteRepository routeRepository,
                           OperatorRepository operatorRepository,
                           ScheduleRepository scheduleRepository,
                           BookingRepository bookingRepository) {
        this.routeRepository = routeRepository;
        this.operatorRepository = operatorRepository;
        this.scheduleRepository = scheduleRepository;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping
    public ResponseEntity<List<Route>> getAllRoutes() {
        return ResponseEntity.ok(routeRepository.findAll());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Route>> searchRoutes(
            @RequestParam String origin,
            @RequestParam String destination) {
        return ResponseEntity.ok(
                routeRepository.findByOriginAndDestination(origin, destination)
        );
    }

    // Admin: create a route. Body: { origin, destination, basePrice, operatorId? }
    @PostMapping
    public ResponseEntity<?> createRoute(@RequestBody Map<String, Object> body) {
        try {
            Operator operator = null;
            Object opId = body.get("operatorId");
            if (opId != null) {
                operator = operatorRepository.findById(UUID.fromString(String.valueOf(opId))).orElse(null);
            }
            if (operator == null) {
                operator = operatorRepository.findAll().stream().findFirst().orElse(null);
            }
            Route route = Route.builder()
                    .operator(operator)
                    .origin(String.valueOf(body.get("origin")))
                    .destination(String.valueOf(body.get("destination")))
                    .basePrice(new BigDecimal(String.valueOf(body.get("basePrice"))))
                    .build();
            return ResponseEntity.ok(routeRepository.save(route));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: update a route's fields.
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRoute(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return routeRepository.findById(id).map(route -> {
            if (body.get("origin") != null) route.setOrigin(String.valueOf(body.get("origin")));
            if (body.get("destination") != null) route.setDestination(String.valueOf(body.get("destination")));
            if (body.get("basePrice") != null) route.setBasePrice(new BigDecimal(String.valueOf(body.get("basePrice"))));
            return ResponseEntity.ok(routeRepository.save(route));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Removes the route and the timetable generated for it. Schedules are
     * regenerated daily so clearing them costs nothing, but a booking is real —
     * if any passenger holds one, refuse rather than delete out from under them
     * (the foreign key would reject it anyway, with a far worse message).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoute(@PathVariable UUID id) {
        if (!routeRepository.existsById(id)) return ResponseEntity.notFound().build();

        List<Schedule> schedules = scheduleRepository.findAll().stream()
                .filter(s -> s.getRoute() != null && id.equals(s.getRoute().getId()))
                .toList();

        long booked = schedules.stream()
                .flatMap(s -> bookingRepository.findBySchedule_Id(s.getId()).stream())
                .filter(b -> !"cancelled".equalsIgnoreCase(b.getStatus()))
                .count();
        if (booked > 0) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    booked + (booked == 1 ? " booking exists" : " bookings exist")
                            + " on this route — cancel them first"));
        }

        scheduleRepository.deleteAll(schedules);
        routeRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true, "schedulesRemoved", schedules.size()));
    }
}
