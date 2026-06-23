package com.charlesSchwab.account_service.controller;

import com.charlesSchwab.account_service.entity.AppliedTransaction;
import com.charlesSchwab.account_service.enums.TransactionType;
import com.charlesSchwab.account_service.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void applyValidTransactionReturnsOk() throws Exception {
        AppliedTransaction saved = new AppliedTransaction();
        saved.setEventId("evt-100");
        saved.setAccountId("acct-123");
        saved.setType(TransactionType.CREDIT);
        saved.setAmount(new BigDecimal("10.00"));
        saved.setCurrency("USD");
        saved.setEventTimestamp(Instant.parse("2026-05-15T14:02:11Z"));

        when(accountService.apply(any(AppliedTransaction.class))).thenReturn(saved);

        String body = """
                {
                  "eventId": "evt-100",
                  "type": "CREDIT",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-100"))
                .andExpect(jsonPath("$.accountId").value("acct-123"))
                .andExpect(jsonPath("$.type").value("CREDIT"));

        verify(accountService).apply(any(AppliedTransaction.class));
    }

    @Test
    void applyUnknownTransactionTypeReturnsBadRequest() throws Exception {
        String body = """
                {
                  "eventId": "evt-bad",
                  "type": "TRANSFER",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                // NOTE: was asserting lowercase "invalid_transaction_type" -- ApiError.of() uses
                // ErrorCode.name(), which is uppercase. The old assertion would fail if run.
                .andExpect(jsonPath("$.error").value("INVALID_TRANSACTION_TYPE"));

        verify(accountService, never()).apply(any(AppliedTransaction.class));
    }

    @Test
    void applyWithMissingRequiredFieldReturnsBadRequest() throws Exception {
        // currency is missing entirely
        String body = """
                {
                  "eventId": "evt-missing-field",
                  "type": "CREDIT",
                  "amount": 10.00,
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verify(accountService, never()).apply(any(AppliedTransaction.class));
    }

    @Test
    void applyWithZeroAmountReturnsBadRequest() throws Exception {
        String body = """
                {
                  "eventId": "evt-zero",
                  "type": "CREDIT",
                  "amount": 0,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verify(accountService, never()).apply(any(AppliedTransaction.class));
    }

    @Test
    void applyWithNegativeAmountReturnsBadRequest() throws Exception {
        String body = """
                {
                  "eventId": "evt-negative",
                  "type": "DEBIT",
                  "amount": -10.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verify(accountService, never()).apply(any(AppliedTransaction.class));
    }
}