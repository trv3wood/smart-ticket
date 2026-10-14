package com.example.ticket.api;

import com.example.ticket.domain.*;
import com.example.ticket.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.net.URI;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final TicketService service;
    public TicketController(TicketService service) { this.service = service; }
    @PostMapping
    ResponseEntity<Ticket> create(@Valid @RequestBody Requests.Create request, Principal actor) {
        Ticket ticket = service.create(request, actor.getName());
        return ResponseEntity.created(URI.create("/api/tickets/" + ticket.getId())).body(ticket);
    }
    @GetMapping("/{id}")
    Ticket detail(@PathVariable @Positive long id) { return service.detail(id); }
    @GetMapping
    TicketService.Page page(@RequestParam(required=false) Status status,
        @RequestParam(required=false) @Positive Long beforeId,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.page(status, beforeId, size);
    }
    @PostMapping("/{id}/transitions")
    Ticket transition(@PathVariable @Positive long id,
        @Valid @RequestBody Requests.Transition request, Principal actor) {
        return service.transition(id, request, actor.getName());
    }
    @GetMapping("/{id}/events")
    List<TicketEvent> events(@PathVariable @Positive long id) { return service.events(id); }
}
