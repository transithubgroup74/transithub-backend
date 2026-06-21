package com.transithub.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private String momoReference;

    private BigDecimal amount;

    @Builder.Default
    private String status = "pending";

    private LocalDateTime paidAt;
}