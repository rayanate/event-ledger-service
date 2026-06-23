package com.charlesSchwab.account_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRequest(
        @NotBlank String eventId,
        @NotBlank String type,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank String currency,
        @NotNull Instant eventTimestamp
) {}