package com.example.ticket.service;
import com.example.ticket.domain.Ticket;
public interface TicketCache {
    Ticket get(long id);
    void put(Ticket ticket);
    void evict(long id);
}
