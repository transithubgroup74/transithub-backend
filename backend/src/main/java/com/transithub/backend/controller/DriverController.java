package com.transithub.backend.controller;

import com.transithub.backend.model.Driver;
import com.transithub.backend.model.Operator;
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
 * Fleet management — drivers. Powers the admin dashboard Drivers page (list,
 * add, delete). Returns flattened maps (no nested operator passwordHash).
 * Open (no auth) for the demo, like the other admin APIs.
 */
@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverRepository driverRepository;
    private final OperatorRepository operatorRepository;

    public DriverController(DriverRepository driverRepository, OperatorRepository operatorRepository) {
        this.driverRepository = driverRepository;
        this.operatorRepository = operatorRepository;
    }

    @GetMapping
    public List<Map<String, Object>> all() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Driver d : driverRepository.findAll()) {
            out.add(flatten(d));
        }
        return out;
    }

    // Body: { name, phone, licenseNumber, status?, operatorId? | company? }
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        try {
            Operator operator = resolveOperator(body.get("operatorId"), body.get("company"));
            Driver driver = Driver.builder()
                    .operator(operator)
                    .name(str(body.get("name")))
                    .phone(str(body.get("phone")))
                    .licenseNumber(str(body.get("licenseNumber")))
                    .status(body.get("status") != null ? str(body.get("status")) : "active")
                    .build();
            driverRepository.save(driver);
            return ResponseEntity.ok(flatten(driver));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        if (!driverRepository.existsById(id)) return ResponseEntity.notFound().build();
        driverRepository.deleteById(id);
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

    private Map<String, Object> flatten(Driver d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId().toString());
        m.put("name", d.getName());
        m.put("phone", d.getPhone());
        m.put("licenseNumber", d.getLicenseNumber());
        m.put("status", d.getStatus());
        m.put("company", d.getOperator() != null ? d.getOperator().getCompanyName() : "");
        m.put("operatorId", d.getOperator() != null ? d.getOperator().getId().toString() : null);
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
