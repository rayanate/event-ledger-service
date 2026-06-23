package com.charlesSchwab.account_service.controller;

import com.charlesSchwab.account_service.dto.TransactionRequest;
import com.charlesSchwab.account_service.entity.AppliedTransaction;
import com.charlesSchwab.account_service.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<AppliedTransaction> apply(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest req) {
        var txn = new AppliedTransaction(); // map fields from req + accountId
        txn.setEventId(req.eventId());
        txn.setAccountId(accountId);
        txn.setType(req.type());
        txn.setAmount(req.amount());
        txn.setCurrency(req.currency());
        txn.setEventTimestamp(req.eventTimestamp());
        return ResponseEntity.ok(service.apply(txn));
    }

    @GetMapping("/{accountId}/balance")
    public Map<String, Object> balance(@PathVariable String accountId) {
        return Map.of("accountId", accountId, "balance", service.balance(accountId));
    }

    @GetMapping("/{accountId}")
    public Map<String, Object> account(@PathVariable String accountId) {
        return Map.of(
                "accountId", accountId,
                "balance", service.balance(accountId),
                "transactions", service.transactions(accountId));
    }
}