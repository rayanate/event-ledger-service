package com.charlesSchwab.account_service.service;

import com.charlesSchwab.account_service.entity.AppliedTransaction;
import com.charlesSchwab.account_service.enums.TransactionType;
import com.charlesSchwab.account_service.repository.AppliedTransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class AccountService {
    private final AppliedTransactionRepository repo;
    public AccountService(AppliedTransactionRepository repo) { this.repo = repo; }

    @Transactional
    public AppliedTransaction apply(AppliedTransaction txn) {
        // Fast path: already applied -> no-op, return existing (idempotent)
        Optional<AppliedTransaction> existing = repo.findById(txn.getEventId());
        if (existing.isPresent()) return existing.get();
        try {
            return repo.save(txn);
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate hit the PK constraint — treat as already applied
            return repo.findById(txn.getEventId()).orElseThrow();
        }
    }

    public BigDecimal balance(String accountId) {
        return repo.findByAccountId(accountId).stream()
                .map(t -> t.getType() == TransactionType.CREDIT ? t.getAmount() : t.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<AppliedTransaction> transactions(String accountId) {
        return repo.findByAccountId(accountId);
    }
}