package com.transithub.backend.controller;

import com.transithub.backend.model.Bus;
import com.transithub.backend.model.Driver;
import com.transithub.backend.model.Operator;
import com.transithub.backend.repository.BusRepository;
import com.transithub.backend.repository.DriverRepository;
import com.transithub.backend.repository.OperatorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fleet management — buses. Powers the admin dashboard Buses page (list,
 * add, delete). Returns flattened maps so the operator's passwordHash is
 * never serialized. Open (no auth) for the demo, like the other admin APIs.
 */
@RestController
@RequestMapping("/api/buses")
public class BusController {

    private final BusRepository busRepository;
    private final OperatorRepository operatorRepository;
    private final DriverRepository driverRepository;

    public BusController(BusRepository busRepository,
                         OperatorRepository operatorRepository,
                         DriverRepository driverRepository) {
        this.busRepository = busRepository;
        this.operatorRepository = operatorRepository;
        this.driverRepository = driverRepository;
    }

    @GetMapping
    public List<Map<String, Object>> all() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Bus b : busRepository.findAll()) {
            out.add(flatten(b));
        }
        return out;
    }

    // Body: { plateNumber, capacity, model, status?, operatorId? | company? }
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        try {
            Operator operator = resolveOperator(body.get("operatorId"), body.get("company"));
            Bus bus = Bus.builder()
                    .operator(operator)
                    .plateNumber(str(body.get("plateNumber")))
                    .capacity(parseCapacity(body.get("capacity")))
                    .model(str(body.get("model")))
                    .status(body.get("status") != null ? str(body.get("status")) : "active")
                    .build();
            busRepository.save(bus);
            return ResponseEntity.ok(flatten(bus));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Assign (or clear) the bus's driver. Body: { driverId } — pass null or an
     * empty string to unassign. Refuses a driver from a different company, so
     * an operator can't end up with someone else's driver on their bus.
     */
    @PutMapping("/{id}/driver")
    public ResponseEntity<?> assignDriver(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Bus bus = busRepository.findById(id).orElse(null);
        if (bus == null) return ResponseEntity.notFound().build();

        Object raw = body.get("driverId");
        if (raw == null || String.valueOf(raw).isBlank()) {
            bus.setDriver(null);
            busRepository.save(bus);
            return ResponseEntity.ok(flatten(bus));
        }

        Driver driver;
        try {
            driver = driverRepository.findById(UUID.fromString(String.valueOf(raw))).orElse(null);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid driver id"));
        }
        if (driver == null) return ResponseEntity.badRequest().body(Map.of("error", "Driver not found"));

        if (bus.getOperator() != null && driver.getOperator() != null
                && !bus.getOperator().getId().equals(driver.getOperator().getId())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", driver.getName() + " belongs to a different company"));
        }

        bus.setDriver(driver);
        busRepository.save(bus);
        return ResponseEntity.ok(flatten(bus));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        if (!busRepository.existsById(id)) return ResponseEntity.notFound().build();
        busRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private Operator resolveOperator(Object operatorId, Object company) {
        if (operatorId != null && !String.valueOf(operatorId).isBlank()) {
            Operator op = operatorRepository.findById(UUID.fromString(String.valueOf(operatorId))).orElse(null);
            if (op != null) return op;
        }
        if (company != null && !String.valueOf(company).isBlank()) {
            String name = String.valueOf(company).trim();
            Operator op = operatorRepository.findAll().stream()
                    .filter(o -> name.equalsIgnoreCase(o.getCompanyName()))
                    .findFirst().orElse(null);
            if (op != null) return op;
        }
        return operatorRepository.findAll().stream().findFirst().orElse(null);
    }

    private Map<String, Object> flatten(Bus b) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", b.getId().toString());
        m.put("plateNumber", b.getPlateNumber());
        m.put("capacity", b.getCapacity());
        m.put("model", b.getModel());
        m.put("status", b.getStatus() != null ? b.getStatus() : "active");
        m.put("company", b.getOperator() != null ? b.getOperator().getCompanyName() : "");
        m.put("operatorId", b.getOperator() != null ? b.getOperator().getId().toString() : null);
        m.put("driverId", b.getDriver() != null ? b.getDriver().getId().toString() : null);
        m.put("driverName", b.getDriver() != null ? b.getDriver().getName() : null);
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer parseCapacity(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(o).trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
