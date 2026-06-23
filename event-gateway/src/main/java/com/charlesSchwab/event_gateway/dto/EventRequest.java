package com.charlesSchwab.event_gateway.dto;

import com.charlesSchwab.event_gateway.model.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record EventRequest(
        @NotBlank String eventId,
        @NotBlank String accountId,
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank String currency,
        @NotNull Instant eventTimestamp,
        JsonNode metadata          // optional, arbitrary JSON — not validated
) {}
