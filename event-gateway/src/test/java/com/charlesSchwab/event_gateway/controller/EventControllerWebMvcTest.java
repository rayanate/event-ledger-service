package com.charlesSchwab.event_gateway.controller;

import com.charlesSchwab.event_gateway.entity.EventRecord;
import com.charlesSchwab.event_gateway.exception.AccountServiceUnavailableException;
import com.charlesSchwab.event_gateway.exception.EventNotFoundException;
import com.charlesSchwab.event_gateway.model.TransactionType;
import com.charlesSchwab.event_gateway.service.EventResult;
import com.charlesSchwab.event_gateway.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Test
    void submitNewEventReturnsCreated() throws Exception {
        EventRecord event = sampleEvent("evt-1", "acct-123", Instant.parse("2026-05-15T14:02:11Z"));
        when(eventService.submit(any(EventRecord.class))).thenReturn(new EventResult(event, true));

        String body = """
                {
                  "eventId": "evt-1",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 15.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z",
                  "metadata": {"source":"test"}
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-1"));
    }

    @Test
    void submitReplayReturnsOk() throws Exception {
        EventRecord event = sampleEvent("evt-1", "acct-123", Instant.parse("2026-05-15T14:02:11Z"));
        when(eventService.submit(any(EventRecord.class))).thenReturn(new EventResult(event, false));

        String body = """
                {
                  "eventId": "evt-1",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 15.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-1"));
    }

    @Test
    void getByIdNotFoundReturns404() throws Exception {
        when(eventService.getById("missing")).thenThrow(new EventNotFoundException("missing"));

        mockMvc.perform(get("/events/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EVENT_NOT_FOUND"));
    }

    @Test
    void submitWhenAccountServiceDownReturns503() throws Exception {
        when(eventService.submit(any(EventRecord.class)))
                .thenThrow(new AccountServiceUnavailableException("down", new RuntimeException("boom")));

        String body = """
                {
                  "eventId": "evt-down",
                  "accountId": "acct-123",
                  "type": "DEBIT",
                  "amount": 5.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("ACCOUNT_SERVICE_UNAVAILABLE"));
    }

    @Test
    void listByAccountReturnsChronologicalEvents() throws Exception {
        EventRecord earlier = sampleEvent("evt-1", "acct-123", Instant.parse("2026-05-15T10:00:00Z"));
        EventRecord later = sampleEvent("evt-2", "acct-123", Instant.parse("2026-05-15T12:00:00Z"));
        when(eventService.listByAccount("acct-123")).thenReturn(List.of(earlier, later));

        mockMvc.perform(get("/events").param("account", "acct-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-1"))
                .andExpect(jsonPath("$[1].eventId").value("evt-2"));
    }

    private static EventRecord sampleEvent(String id, String accountId, Instant ts) {
        EventRecord e = new EventRecord();
        e.setEventId(id);
        e.setAccountId(accountId);
        e.setType(TransactionType.CREDIT);
        e.setAmount(new BigDecimal("15.00"));
        e.setCurrency("USD");
        e.setEventTimestamp(ts);
        e.setReceivedAt(Instant.parse("2026-05-15T14:02:12Z"));
        e.setMetadata("{\"source\":\"test\"}");
        return e;
    }
}