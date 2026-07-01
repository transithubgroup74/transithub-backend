package com.transithub.backend.controller;

import com.transithub.backend.model.Alert;
import com.transithub.backend.repository.AlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Broadcast alerts: the admin dashboard posts them, the passenger app
 * pulls them into its notifications tab. Open (no auth) for the demo,
 * like the other admin APIs.
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public List<Alert> latest() {
        return alertRepository.findTop50ByOrderByCreatedAtDesc();
    }

    // Body: { title, message, type?, audience?, sentBy? }
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String title = str(body.get("title"));
        String message = str(body.get("message"));
        if (title == null || title.isBlank() || message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title and message are required"));
        }
        Alert alert = Alert.builder()
                .title(title.trim())
                .message(message.trim())
                .type(str(body.get("type")))
                .audience(str(body.get("audience")))
                .sentBy(str(body.get("sentBy")))
                .build();
        return ResponseEntity.ok(alertRepository.save(alert));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        if (!alertRepository.existsById(id)) return ResponseEntity.notFound().build();
        alertRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
