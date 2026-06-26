package com.transithub.backend.controller;

import com.transithub.backend.model.Operator;
import com.transithub.backend.model.Route;
import com.transithub.backend.repository.OperatorRepository;
import com.transithub.backend.repository.RouteRepository;
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

    public RouteController(RouteRepository routeRepository, OperatorRepository operatorRepository) {
        this.routeRepository = routeRepository;
        this.operatorRepository = operatorRepository;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoute(@PathVariable UUID id) {
        if (!routeRepository.existsById(id)) return ResponseEntity.notFound().build();
        routeRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
