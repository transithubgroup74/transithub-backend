package com.transithub.backend.controller;

import com.transithub.backend.model.Ticket;
import com.transithub.backend.service.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/issue")
    public ResponseEntity<Ticket> issueTicket(@RequestBody Map<String, String> body) {
        UUID bookingId = UUID.fromString(body.get("bookingId"));
        return ResponseEntity.ok(ticketService.issueTicket(bookingId));
    }

    @PostMapping("/scan")
    public ResponseEntity<Ticket> scanTicket(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String qrCodeHash = body.get("qrCodeHash");
        String conductorId = authentication.getName();
        return ResponseEntity.ok(ticketService.scanTicket(qrCodeHash, conductorId));
    }
}