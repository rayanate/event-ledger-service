package com.charlesSchwab.event_gateway.service;

import java.math.BigDecimal;

/**
 * Mirrors the JSON shape returned by Account Service's
 * GET /accounts/{accountId}/balance: {"accountId": "...", "balance": 123.45}.
 */
public record BalanceResponse(String accountId, BigDecimal balance) {}