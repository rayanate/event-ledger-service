package com.charlesSchwab.event_gateway.controller;

import com.charlesSchwab.event_gateway.client.AccountClient;
import com.charlesSchwab.event_gateway.service.BalanceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Account Service is internal-only per the architecture -- clients should never
 * call it directly. This proxies balance reads through the Gateway so that:
 *  - balance queries go through the same public entry point as everything else
 *  - when the Account Service is unreachable, the call fails the same way the
 *    POST /events write path does: AccountClient throws
 *    AccountServiceUnavailableException, which ApiExceptionHandler maps to a
 *    clean 503 instead of a hang or a 500.
 */
@RestController
@RequestMapping("/accounts")
public class AccountProxyController {

    private final AccountClient accountClient;

    public AccountProxyController(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse balance(@PathVariable String accountId) {
        return accountClient.getBalance(accountId);
    }
}