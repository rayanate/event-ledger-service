package com.charlesSchwab.account_service.service;

import com.charlesSchwab.account_service.entity.AppliedTransaction;
import com.charlesSchwab.account_service.enums.TransactionType;
import com.charlesSchwab.account_service.repository.AppliedTransactionRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AppliedTransactionRepository repo;
    public AccountService(AppliedTransactionRepository repo) { this.repo = repo; }

    @Transactional
    public AppliedTransaction apply(AppliedTransaction txn) {
        // Fast path: already applied -> no-op, return existing (idempotent)
        Optional<AppliedTransaction> existing = repo.findById(txn.getEventId());
        if (existing.isPresent()) {
            log.info("txn.duplicate eventId={} accountId={}", txn.getEventId(), txn.getAccountId());
            return existing.get();
        }
        try {
            AppliedTransaction saved = repo.save(txn);
            log.info("txn.apply.ok eventId={} accountId={} type={} amount={}",
                    saved.getEventId(), saved.getAccountId(), saved.getType(), saved.getAmount());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate hit the PK constraint — treat as already applied
            log.warn("txn.concurrent_duplicate eventId={} accountId={}", txn.getEventId(), txn.getAccountId());
            return repo.findById(txn.getEventId()).orElseThrow();
        }
    }

    public BigDecimal balance(String accountId) {
        return repo.findByAccountId(accountId).stream()
                .map(t -> t.getType() == TransactionType.CREDIT ? t.getAmount() : t.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<AppliedTransaction> transactions(String accountId) {
        return repo.findByAccountIdOrderByEventTimestampAsc(accountId);
    }
}