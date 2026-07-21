package com.transithub.backend.controller;

import com.transithub.backend.config.JwtUtil;
import com.transithub.backend.model.Staff;
import com.transithub.backend.repository.StaffRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    // The logged-in staff updates their own profile (name / email / password).
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, Object> body, Authentication authentication) {
        Staff s = staffRepository.findByStaffId(authentication.getName()).orElse(null);
        if (s == null) return ResponseEntity.status(404).body(Map.of("error", "Staff not found"));
        if (body.get("name") != null) s.setName(str(body.get("name")));
        if (body.get("email") != null) s.setEmail(str(body.get("email")));
        Object pw = body.get("password");
        if (pw != null && !String.valueOf(pw).isBlank()) {
            s.setPasswordHash(passwordEncoder.encode(String.valueOf(pw)));
        }
        staffRepository.save(s);
        Map<String, Object> m = new HashMap<>();
        m.put("staffId", s.getStaffId());
        m.put("name", s.getName());
        m.put("email", s.getEmail());
        m.put("role", s.getRole());
        m.put("company", s.getCompany());
        return ResponseEntity.ok(m);
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

    /**
     * Create a staff account. The dashboard used to keep new staff in the
     * browser's localStorage, so they looked added but could never log in —
     * login checks this table. Super Admin only.
     * Body: { name, email, role, company, password, staffId? }
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, Authentication authentication) {
        Staff caller = staffRepository.findByStaffId(authentication.getName()).orElse(null);
        if (caller == null || !"Super Admin".equalsIgnoreCase(caller.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only a Super Admin can add staff"));
        }

        String name = trim(body.get("name"));
        String email = trim(body.get("email"));
        String role = trim(body.get("role"));
        String company = trim(body.get("company"));
        String password = str(body.get("password"));

        if (isBlank(name) || isBlank(role) || isBlank(company) || isBlank(password)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name, role, company and password are required"));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }

        String staffId = trim(body.get("staffId"));
        if (isBlank(staffId)) staffId = generateStaffId(role, company);
        staffId = staffId.toUpperCase();
        if (staffRepository.findByStaffId(staffId).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Staff ID " + staffId + " is already taken"));
        }

        Staff s = Staff.builder()
                .staffId(staffId)
                .name(name)
                .email(email)
                .role(role)
                .company(company)
                .passwordHash(passwordEncoder.encode(password))
                .status("active")
                .build();
        staffRepository.save(s);

        Map<String, Object> m = new HashMap<>();
        m.put("staffId", s.getStaffId());
        m.put("name", s.getName());
        m.put("email", s.getEmail());
        m.put("role", s.getRole());
        m.put("company", s.getCompany());
        m.put("status", s.getStatus());
        return ResponseEntity.ok(m);
    }

    /** Remove a staff account. Super Admin only, and never your own login. */
    @DeleteMapping("/{staffId}")
    public ResponseEntity<?> delete(@PathVariable String staffId, Authentication authentication) {
        Staff caller = staffRepository.findByStaffId(authentication.getName()).orElse(null);
        if (caller == null || !"Super Admin".equalsIgnoreCase(caller.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only a Super Admin can remove staff"));
        }
        String id = staffId.trim().toUpperCase();
        if (id.equalsIgnoreCase(caller.getStaffId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You can't remove your own account"));
        }
        Staff s = staffRepository.findByStaffId(id).orElse(null);
        if (s == null) return ResponseEntity.notFound().build();
        staffRepository.delete(s);
        return ResponseEntity.ok(Map.of("deleted", true, "staffId", id));
    }

    /** Mirrors the dashboard's scheme, e.g. VIP-OP-03 — role code, company code, running number. */
    private String generateStaffId(String role, String company) {
        Map<String, String> roleMap = Map.of(
                "super admin", "AD", "manager", "MG", "operator", "OP", "conductor", "CN");
        Map<String, String> companyMap = Map.of(
                "transithub hq", "HQ", "vip jeoun", "VIP", "oa express", "OA", "stc", "STC",
                "kingdom transport", "KGD", "night rider express", "NR", "metro mass transit", "MMT");

        String roleCode = roleMap.getOrDefault(role.toLowerCase(), "ST");
        String compCode = companyMap.get(company.toLowerCase());
        if (compCode == null) {
            String cleaned = company.replaceAll("[^A-Za-z ]", "").trim();
            String[] words = cleaned.isEmpty() ? new String[0] : cleaned.split("\\s+");
            if (words.length > 1) {
                StringBuilder sb = new StringBuilder();
                for (String w : words) sb.append(Character.toUpperCase(w.charAt(0)));
                compCode = sb.substring(0, Math.min(3, sb.length()));
            } else if (words.length == 1) {
                compCode = words[0].toUpperCase().substring(0, Math.min(3, words[0].length()));
            } else {
                compCode = "TH";
            }
        }
        int next = staffRepository.findAll().size() + 1;
        String candidate;
        do {
            candidate = String.format("%s-%s-%02d", compCode, roleCode, next++);
        } while (staffRepository.findByStaffId(candidate).isPresent());
        return candidate;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
