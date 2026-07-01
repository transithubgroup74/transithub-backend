package com.transithub.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A broadcast alert sent from the admin dashboard to passengers.
 * The app pulls these and shows them in its notifications tab.
 */
@Entity
@Table(name = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;

    @Column(length = 1000)
    private String message;

    private String type;      // e.g. "General Announcement", "Route Delay"

    private String audience;  // e.g. "All Passengers"

    private String sentBy;    // staff id + name from the dashboard

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
