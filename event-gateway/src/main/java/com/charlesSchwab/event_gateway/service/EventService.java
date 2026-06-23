package com.charlesSchwab.event_gateway.service;

import com.charlesSchwab.account_service.dto.TransactionRequest;
import com.charlesSchwab.event_gateway.client.AccountClient;
import com.charlesSchwab.event_gateway.entity.EventRecord;
import com.charlesSchwab.event_gateway.exception.EventNotFoundException;
import com.charlesSchwab.event_gateway.repository.EventRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRecordRepository repo;
    private final AccountClient accountClient;

    public EventService(EventRecordRepository repo, AccountClient accountClient) {
        this.repo = repo;
        this.accountClient = accountClient;
    }

    public EventResult submit(EventRecord event) {
        // 1. Idempotency short-circuit — replay returns existing, no Account Service call
        Optional<EventRecord> existing = repo.findById(event.getEventId());
        if (existing.isPresent()) {
            log.info("event.replay eventId={} accountId={}", event.getEventId(), event.getAccountId());
            return new EventResult(existing.get(), false); // false = not newly created
        }
        // 2. Apply downstream FIRST. If this throws, we never persist -> no orphan.
        log.info("event.submit.start eventId={} accountId={} type={} amount={}",
                event.getEventId(), event.getAccountId(), event.getType(), event.getAmount());
        accountClient.applyTransaction(event.getAccountId(), toTransactionRequest(event));
        // 3. Only persist after a successful apply
        EventRecord saved = repo.save(event);
        log.info("event.submit.ok eventId={} accountId={}", saved.getEventId(), saved.getAccountId());
        return new EventResult(saved, true); // true = created
    }

    public EventRecord getById(String eventId) {
        return repo.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("event.not_found eventId={}", eventId);
                    return new EventNotFoundException(eventId);
                });
    }

    public List<EventRecord> listByAccount(String accountId) {
        return repo.findByAccountIdOrderByEventTimestampAsc(accountId); // sort on read
    }

    private TransactionRequest toTransactionRequest(EventRecord event) {
        return new TransactionRequest(
                event.getEventId(),
                event.getType(),
                event.getAmount(),
                event.getCurrency(),
                event.getEventTimestamp()
        );
    }
}