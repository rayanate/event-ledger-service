package com.charlesSchwab.event_gateway.controller;

import com.charlesSchwab.event_gateway.client.AccountClient;
import com.charlesSchwab.event_gateway.service.BalanceResponse;
import com.charlesSchwab.event_gateway.exception.AccountServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountProxyController.class)
class AccountProxyControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountClient accountClient;

    @Test
    void balanceReturnsOkWhenAccountServiceIsUp() throws Exception {
        when(accountClient.getBalance("acct-123"))
                .thenReturn(new BalanceResponse("acct-123", new BigDecimal("150.00")));

        mockMvc.perform(get("/accounts/acct-123/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-123"))
                .andExpect(jsonPath("$.balance").value(150.00));
    }

    @Test
    void balanceReturns503WhenAccountServiceIsUnreachable() throws Exception {
        when(accountClient.getBalance("acct-123"))
                .thenThrow(new AccountServiceUnavailableException("down", new RuntimeException("boom")));

        mockMvc.perform(get("/accounts/acct-123/balance"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("ACCOUNT_SERVICE_UNAVAILABLE"));
    }
}