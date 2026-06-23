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
                .andExpect(jsonPath("$.error").value("invalid_transaction_type"));

        verify(accountService, never()).apply(any(AppliedTransaction.class));
    }
}

