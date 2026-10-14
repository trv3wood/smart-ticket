package com.example.ticket.domain;
import java.time.LocalDateTime;
public record TicketEvent(Long id, Long ticketId, String fromStatus, String toStatus,
                          String actor, String note, LocalDateTime createdAt) {}
