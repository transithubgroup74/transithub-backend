package com.transithub.backend.service;

import com.transithub.backend.model.Booking;
import com.transithub.backend.model.Ticket;
import com.transithub.backend.repository.BookingRepository;
import com.transithub.backend.repository.TicketRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final BookingRepository bookingRepository;

    public TicketService(TicketRepository ticketRepository,
                         BookingRepository bookingRepository) {
        this.ticketRepository = ticketRepository;
        this.bookingRepository = bookingRepository;
    }

    public Ticket issueTicket(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        String qrHash = UUID.randomUUID().toString();

        Ticket ticket = Ticket.builder()
                .booking(booking)
                .qrCodeHash(qrHash)
                .build();

        return ticketRepository.save(ticket);
    }

    public Ticket scanTicket(String qrCodeHash, String conductorId) {
        Ticket ticket = ticketRepository.findByQrCodeHash(qrCodeHash)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (ticket.getScannedAt() != null) {
            throw new RuntimeException("Ticket already scanned");
        }

        ticket.setScannedAt(LocalDateTime.now());
        ticket.setScannedBy(conductorId);
        return ticketRepository.save(ticket);
    }
}