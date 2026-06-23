package com.charlesSchwab.event_gateway.service;

import com.charlesSchwab.event_gateway.client.AccountClient;
import com.charlesSchwab.event_gateway.entity.EventRecord;
import com.charlesSchwab.event_gateway.model.TransactionType;
import com.charlesSchwab.event_gateway.repository.EventRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRecordRepository repo;

    @Mock
    private AccountClient accountClient;

    @Test
    void concurrentDuplicateSubmitReturnsExistingInsteadOfFailing() {
        // Simulates two requests for the same eventId racing past the findById idempotency
        // check at (almost) the same time. This thread "loses" the INSERT -- the PK
        // constraint rejects it because the other request's save already won.
        EventService service = new EventService(repo, accountClient);
        EventRecord event = sampleEvent();
        EventRecord persistedByWinner = sampleEvent();

        when(repo.findById(event.getEventId()))
                .thenReturn(Optional.empty())                 // pre-save idempotency check: not found yet
                .thenReturn(Optional.of(persistedByWinner));   // re-fetch after losing the save race

        doNothing().when(accountClient).applyTransaction(anyString(), any());
        when(repo.save(event)).thenThrow(new DataIntegrityViolationException("duplicate key: evt-race-1"));

        EventResult result = service.submit(event);

        assertThat(result.created()).isFalse();
        assertThat(result.event()).isSameAs(persistedByWinner);
    }

    private static EventRecord sampleEvent() {
        EventRecord e = new EventRecord();
        e.setEventId("evt-race-1");
        e.setAccountId("acct-1");
        e.setType(TransactionType.CREDIT);
        e.setAmount(new BigDecimal("10.00"));
        e.setCurrency("USD");
        e.setEventTimestamp(Instant.parse("2026-05-15T10:00:00Z"));
        e.setReceivedAt(Instant.now());
        return e;
    }
}