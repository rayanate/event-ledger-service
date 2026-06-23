package com.charlesSchwab.event_gateway.controller;

import com.charlesSchwab.event_gateway.dto.EventRequest;
import com.charlesSchwab.event_gateway.entity.EventRecord;
import com.charlesSchwab.event_gateway.exception.RateLimitExceededException;
import com.charlesSchwab.event_gateway.service.EventResult;
import com.charlesSchwab.event_gateway.service.EventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;
    private final ObjectMapper objectMapper;

    public EventController(EventService eventService, ObjectMapper objectMapper) {
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @RateLimiter(name = "eventSubmission", fallbackMethod = "submitRateLimited")
    public ResponseEntity<EventRecord> submit(@Valid @RequestBody EventRequest req) {
        EventResult result = eventService.submit(toEventRecord(req));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK; // 201 new, 200 replay
        return ResponseEntity.status(status).body(result.event());
    }

    @SuppressWarnings("unused")
    private ResponseEntity<EventRecord> submitRateLimited(EventRequest req, Throwable throwable) {
        if (throwable instanceof RequestNotPermitted) {
            throw new RateLimitExceededException("Rate limit exceeded for event submission", throwable);
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException(throwable);
    }

    @GetMapping("/{eventId}")
    public EventRecord getById(@PathVariable String eventId) {
        return eventService.getById(eventId); // throws EventNotFoundException -> 404
    }

    @GetMapping
    public List<EventRecord> listByAccount(@RequestParam("account") String accountId) {
        return eventService.listByAccount(accountId); // sorted by eventTimestamp asc
    }

    private EventRecord toEventRecord(EventRequest req) {
        EventRecord e = new EventRecord();
        e.setEventId(req.eventId());
        e.setAccountId(req.accountId());
        e.setType(req.type());
        e.setAmount(req.amount());
        e.setCurrency(req.currency());
        e.setEventTimestamp(req.eventTimestamp());
        e.setReceivedAt(Instant.now());
        e.setMetadata(writeMetadata(req.metadata()));
        return e;
    }

    private String writeMetadata(Object metadata) {
        if (metadata == null) return null;
        try {
            return objectMapper.writeValueAsString(metadata); // store JSON as a string
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid metadata", ex);
        }
    }
}
