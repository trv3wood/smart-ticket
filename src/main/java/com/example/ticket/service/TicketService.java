package com.example.ticket.service;

import com.example.ticket.api.*;
import com.example.ticket.domain.*;
import com.example.ticket.mapper.TicketMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;

@Service
public class TicketService {
    private final TicketMapper mapper;
    private final TicketCache cache;
    public TicketService(TicketMapper mapper, TicketCache cache) {
        this.mapper = mapper;
        this.cache = cache;
    }
    @Transactional
    public Ticket create(Requests.Create request, String actor) {
        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
        ticket.setStatus(Status.OPEN);
        ticket.setCreatedBy(actor);
        mapper.insert(ticket);
        mapper.addEvent(ticket.getId(), "NONE", "OPEN", actor, "创建工单");
        return require(ticket.getId());
    }
    public Ticket detail(long id) {
        Ticket cached = cache.get(id);
        if (cached != null) return cached;
        Ticket ticket = require(id);
        cache.put(ticket);
        return ticket;
    }
    public Ticket require(long id) {
        Ticket ticket = mapper.findById(id);
        if (ticket == null) throw new BusinessException(HttpStatus.NOT_FOUND, "工单不存在");
        return ticket;
    }
    public record Summary(Long id, String title, Category category, Priority priority, Status status,
                          String createdBy, int version, java.time.LocalDateTime createdAt) {
        static Summary of(Ticket t) {
            return new Summary(t.getId(), t.getTitle(), t.getCategory(), t.getPriority(),
                t.getStatus(), t.getCreatedBy(), t.getVersion(), t.getCreatedAt());
        }
    }
    public record Page(List<Summary> items, Long nextCursor, boolean hasMore) {}
    public Page page(Status status, Long beforeId, int size) {
        List<Ticket> rows = mapper.page(status, beforeId, size + 1);
        boolean hasMore = rows.size() > size;
        List<Ticket> items = hasMore ? rows.subList(0, size) : rows;
        Long cursor = hasMore ? items.getLast().getId() : null;
        return new Page(items.stream().map(Summary::of).toList(), cursor, hasMore);
    }
    @Transactional
    public Ticket transition(long id, Requests.Transition request, String actor) {
        // 写操作始终读数据库，缓存的最终一致性不影响并发控制
        Ticket current = require(id);
        if (current.getVersion() != request.version())
            throw new BusinessException(HttpStatus.CONFLICT, "工单已被更新，请刷新后重试");
        if (!current.getStatus().canTransitionTo(request.target()))
            throw new BusinessException(HttpStatus.CONFLICT, "不允许该状态流转");
        if (mapper.transition(id, request.version(), current.getStatus(), request.target()) != 1)
            throw new BusinessException(HttpStatus.CONFLICT, "并发更新冲突，请刷新后重试");
        mapper.addEvent(id, current.getStatus().name(), request.target().name(), actor, request.note());
        // 提交后删除缓存，避免回滚时丢失缓存；并发回填或删除失败仍可能短暂陈旧
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.evict(id); }
        });
        return require(id);
    }
    public List<TicketEvent> events(long id) {
        require(id);
        return mapper.events(id);
    }
}
