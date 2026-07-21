package com.transithub.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A dashboard staff account (admin / manager / operator / conductor).
 * Passwords are bcrypt-hashed. Replaces the plaintext accounts that used
 * to live in the dashboard's JavaScript.
 */
@Entity
@Table(name = "staff")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String staffId;   // e.g. HQ-AD-01

    private String name;

    private String email;

    private String role;      // Super Admin / Manager / Operator / Conductor

    private String company;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;

    @Builder.Default
    private String status = "active";

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastLogin;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
