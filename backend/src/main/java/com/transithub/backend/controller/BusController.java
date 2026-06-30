package com.transithub.backend.controller;

import com.transithub.backend.model.Bus;
import com.transithub.backend.model.Operator;
import com.transithub.backend.repository.BusRepository;
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

    public BusController(BusRepository busRepository, OperatorRepository operatorRepository) {
        this.busRepository = busRepository;
        this.operatorRepository = operatorRepository;
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
