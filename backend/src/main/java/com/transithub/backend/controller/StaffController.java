package com.transithub.backend.controller;

import com.transithub.backend.config.JwtUtil;
import com.transithub.backend.model.Staff;
import com.transithub.backend.repository.StaffRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side staff authentication for the admin dashboard. Login is public;
 * it returns a signed token the dashboard sends on every admin request. The
 * staff list is itself staff-only and never exposes the password hash.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public StaffController(StaffRepository staffRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // Body: { staffId, password }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        String staffId = str(body.get("staffId"));
        String password = str(body.get("password"));
        if (staffId == null || password == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid Staff ID or password"));
        }
        Staff s = staffRepository.findByStaffId(staffId.trim().toUpperCase()).orElse(null);
        if (s == null || !"active".equalsIgnoreCase(s.getStatus())
                || !passwordEncoder.matches(password, s.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid Staff ID or password"));
        }
        s.setLastLogin(LocalDateTime.now());
        staffRepository.save(s);

        Map<String, Object> out = new HashMap<>();
        out.put("token", jwtUtil.generateToken(s.getStaffId(), "STAFF"));
        out.put("staffId", s.getStaffId());
        out.put("name", s.getName());
        out.put("email", s.getEmail());
        out.put("role", s.getRole());
        out.put("company", s.getCompany());
        return ResponseEntity.ok(out);
    }

    // Staff-only list for the dashboard's Staff & Roles page. No password hash.
    @GetMapping
    public List<Map<String, Object>> all() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Staff s : staffRepository.findAll()) {
            Map<String, Object> m = new HashMap<>();
            m.put("staffId", s.getStaffId());
            m.put("name", s.getName());
            m.put("email", s.getEmail());
            m.put("role", s.getRole());
            m.put("company", s.getCompany());
            m.put("status", s.getStatus());
            m.put("lastLogin", s.getLastLogin());
            out.add(m);
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
