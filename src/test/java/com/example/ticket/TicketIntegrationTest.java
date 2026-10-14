package com.example.ticket;

import com.example.ticket.api.Requests;
import com.example.ticket.domain.*;
import com.example.ticket.mapper.TicketMapper;
import com.example.ticket.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username="admin", roles="ADMIN")
class TicketIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TicketService service;
    @MockitoSpyBean TicketMapper mapper;
    @MockitoSpyBean com.example.ticket.service.TicketCache cache;

    Ticket create() {
        return service.create(new Requests.Create("支付失败", "用户支付超时", Category.PAYMENT, Priority.HIGH), "admin");
    }
    @Test void authenticationRequired() throws Exception {
        mvc.perform(get("/api/tickets").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
            .andExpect(status().isUnauthorized());
    }
    @Test void basicAuthenticationWorks() throws Exception {
        mvc.perform(get("/api/tickets")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("admin", "demo-password")))
            .andExpect(status().isOk());
    }
    @Test void createAndAudit() throws Exception {
        var result = mvc.perform(post("/api/tickets").contentType("application/json")
            .content("{\"title\":\"登录失败\",\"description\":\"账号锁定\",\"category\":\"ACCOUNT\",\"priority\":\"MEDIUM\"}"))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.createdBy").value("admin"))
            .andExpect(jsonPath("$.status").value("OPEN")).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(get("/api/tickets/{id}/events", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].toStatus").value("OPEN"));
    }
    @Test void rejectsBadRequests() throws Exception {
        mvc.perform(post("/api/tickets").contentType("application/json")
            .content("{\"title\":\"\",\"description\":\"x\",\"category\":\"ACCOUNT\",\"priority\":\"LOW\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets?status=INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets/-1")).andExpect(status().isBadRequest());
    }
    @Test void missingTicketReturns404() throws Exception {
        mvc.perform(get("/api/tickets/99999999")).andExpect(status().isNotFound());
    }
    @Test void transitionAndStaleVersion() throws Exception {
        Ticket ticket = create();
        mvc.perform(post("/api/tickets/{id}/transitions", ticket.getId()).contentType("application/json")
            .content("{\"target\":\"IN_PROGRESS\",\"version\":0,\"note\":\"开始处理\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/tickets/{id}/transitions", ticket.getId()).contentType("application/json")
            .content("{\"target\":\"RESOLVED\",\"version\":0,\"note\":\"旧请求\"}"))
            .andExpect(status().isConflict());
        assertThat(service.events(ticket.getId())).hasSize(2);
        verify(cache).evict(ticket.getId());
    }
    @Test void invalidTransitionDoesNotWrite() throws Exception {
        Ticket ticket = create();
        mvc.perform(post("/api/tickets/{id}/transitions", ticket.getId()).contentType("application/json")
            .content("{\"target\":\"CLOSED\",\"version\":0,\"note\":\"非法跳转\"}"))
            .andExpect(status().isConflict());
        assertThat(service.require(ticket.getId()).getVersion()).isZero();
        assertThat(service.events(ticket.getId())).hasSize(1);
    }
    @Test void cursorDoesNotRepeatRows() throws Exception {
        Ticket older = create();
        Ticket newer = create();
        var result = mvc.perform(get("/api/tickets?status=OPEN&size=1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasMore").value(true)).andReturn();
        long cursor = json.readTree(result.getResponse().getContentAsString()).path("nextCursor").asLong();
        assertThat(cursor).isEqualTo(newer.getId());
        mvc.perform(get("/api/tickets?status=OPEN&size=1&beforeId=" + cursor))
            .andExpect(jsonPath("$.items[0].id").value(older.getId()));
    }
    @Test void concurrentUpdatesOnlyOneWins() throws Exception {
        Ticket ticket = create();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 8).mapToObj(i -> executor.submit(() -> {
                start.await();
                try {
                    service.transition(ticket.getId(), new Requests.Transition(Status.IN_PROGRESS, 0, "并发请求"), "admin");
                    return true;
                } catch (com.example.ticket.api.BusinessException e) {
                    assertThat(e.status().value()).isEqualTo(409);
                    return false;
                }
            })).toList();
            start.countDown();
            int wins = 0;
            for (var future : futures) if (future.get(10, TimeUnit.SECONDS)) wins++;
            assertThat(wins).isEqualTo(1);
        }
        assertThat(service.require(ticket.getId()).getVersion()).isEqualTo(1);
        assertThat(service.events(ticket.getId())).hasSize(2);
    }
    @Test void eventFailureRollsBackTicketUpdate() {
        Ticket ticket = create();
        doThrow(new IllegalStateException("模拟审计写入失败")).when(mapper)
            .addEvent(eq(ticket.getId()), eq("OPEN"), eq("IN_PROGRESS"), anyString(), anyString());
        assertThatThrownBy(() -> service.transition(ticket.getId(),
            new Requests.Transition(Status.IN_PROGRESS, 0, "开始处理"), "admin"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(service.require(ticket.getId()).getStatus()).isEqualTo(Status.OPEN);
        assertThat(service.require(ticket.getId()).getVersion()).isZero();
        assertThat(service.events(ticket.getId())).hasSize(1);
        verify(cache, never()).evict(ticket.getId());
    }
}
