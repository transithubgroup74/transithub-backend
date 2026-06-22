package com.transithub.backend.controller;

import com.transithub.backend.model.Schedule;
import com.transithub.backend.repository.ScheduleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleRepository scheduleRepository;

    public ScheduleController(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
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
