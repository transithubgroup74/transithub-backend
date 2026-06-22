package com.transithub.backend.controller;

import com.transithub.backend.model.Route;
import com.transithub.backend.repository.RouteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteRepository routeRepository;

    public RouteController(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
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
}