package com.transithub.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"schedule_id", "seat_number"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    private Integer seatNumber;

    @Builder.Default
    private String status = "pending";

    private BigDecimal totalAmount;

    @Column(length = 512)
    private String qrCode;

    // Self-contained trip details — used when there is no real Schedule
    // (e.g. demo/mock buses) so the booking can still be saved to the user's
    // account and sync across devices. All nullable to keep existing rows valid.
    private String origin;
    private String destination;
    private String departsAt;
    private String operator;
    private String busClass;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}