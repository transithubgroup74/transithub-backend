package com.transithub.backend.repository;

import com.transithub.backend.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    Optional<Ticket> findByQrCodeHash(String qrCodeHash);
}