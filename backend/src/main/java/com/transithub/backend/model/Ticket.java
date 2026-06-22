package com.transithub.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(unique = true)
    private String qrCodeHash;

    @Column(updatable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime scannedAt;

    private String scannedBy;

    @PrePersist
    protected void onCreate() {
        issuedAt = LocalDateTime.now();
    }
}