package com.transithub.backend.controller;

import com.transithub.backend.model.Operator;
import com.transithub.backend.repository.OperatorRepository;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight list of bus companies (operators) so the admin dashboard can
 * populate company dropdowns when adding buses, drivers, routes or schedules.
 * Returns only safe fields (id + name) — never the passwordHash.
 */
@RestController
@RequestMapping("/api/operators")
public class OperatorController {

    private final OperatorRepository operatorRepository;

    public OperatorController(OperatorRepository operatorRepository) {
        this.operatorRepository = operatorRepository;
    }

    @GetMapping
    public List<Map<String, Object>> all() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Operator o : operatorRepository.findAll()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId().toString());
            m.put("companyName", o.getCompanyName());
            out.add(m);
        }
        return out;
    }
}
