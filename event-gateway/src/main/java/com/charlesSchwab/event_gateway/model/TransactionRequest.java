package com.charlesSchwab.event_gateway.model;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRequest(
        String eventId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp
) {
}

