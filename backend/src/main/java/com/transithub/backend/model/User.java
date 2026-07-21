package com.transithub.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @Column(unique = true)
    private String email;

    private String phone;

    // Never serialize the hash. Endpoints that return the Booking entity pull
    // the User in with it, which was putting bcrypt hashes in API responses.
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;
    private String fcmToken;
    private String platform;

    @Column(columnDefinition = "TEXT")
    private String photoUrl;

    // NULL means the account predates email verification — those are
    // grandfathered in by DataSeeder so existing passengers aren't locked out.
    // FALSE = signed up but hasn't entered the emailed code yet.
    private Boolean emailVerified;

    private String verificationCode;
    private LocalDateTime verificationExpiry;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}