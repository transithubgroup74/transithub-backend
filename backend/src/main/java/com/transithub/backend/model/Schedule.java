package com.transithub.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "route_id")
    private Route route;

    @ManyToOne
    @JoinColumn(name = "bus_id")
    private Bus bus;

    private LocalDateTime departsAt;

    @Builder.Default
    private String status = "active";

    // "seeded" for auto-generated schedules, "admin" for ones created from the
    // dashboard. The passenger app surfaces "admin" schedules on top of its demo
    // listings so dashboard additions show up live.
    private String source;
}