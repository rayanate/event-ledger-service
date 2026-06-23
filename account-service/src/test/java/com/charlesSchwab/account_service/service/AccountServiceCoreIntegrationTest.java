package com.charlesSchwab.account_service.service;

import com.charlesSchwab.account_service.entity.AppliedTransaction;
import com.charlesSchwab.account_service.enums.TransactionType;
import com.charlesSchwab.account_service.repository.AppliedTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AccountServiceCoreIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AppliedTransactionRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void idempotentApplyDoesNotDuplicateAndDoesNotChangeBalance() {
        AppliedTransaction first = txn("evt-1", "acct-1", TransactionType.CREDIT, "10.00", "2026-05-15T12:00:00Z");

        accountService.apply(first);
        accountService.apply(first); // replay

        assertThat(repository.findAll()).hasSize(1);
        assertThat(accountService.balance("acct-1")).isEqualByComparingTo("10.00");
    }

    @Test
    void outOfOrderInsertStillReturnsChronologicalTransactions() {
        accountService.apply(txn("evt-later", "acct-1", TransactionType.DEBIT, "5.00", "2026-05-15T14:00:00Z"));
        accountService.apply(txn("evt-earlier", "acct-1", TransactionType.CREDIT, "20.00", "2026-05-15T10:00:00Z"));

        List<AppliedTransaction> txns = accountService.transactions("acct-1");

        assertThat(txns).extracting(AppliedTransaction::getEventId)
                .containsExactly("evt-earlier", "evt-later");
    }

    @Test
    void balanceIsCreditMinusDebit() {
        accountService.apply(txn("evt-c1", "acct-2", TransactionType.CREDIT, "100.00", "2026-05-15T10:00:00Z"));
        accountService.apply(txn("evt-d1", "acct-2", TransactionType.DEBIT, "25.00", "2026-05-15T11:00:00Z"));
        accountService.apply(txn("evt-c2", "acct-2", TransactionType.CREDIT, "10.00", "2026-05-15T12:00:00Z"));

        assertThat(accountService.balance("acct-2")).isEqualByComparingTo("85.00");
    }

    private static AppliedTransaction txn(String eventId,
                                          String accountId,
                                          TransactionType type,
                                          String amount,
                                          String timestamp) {
        AppliedTransaction appliedTransaction = new AppliedTransaction();
        appliedTransaction.setEventId(eventId);
        appliedTransaction.setAccountId(accountId);
        appliedTransaction.setType(type);
        appliedTransaction.setAmount(new BigDecimal(amount));
        appliedTransaction.setCurrency("USD");
        appliedTransaction.setEventTimestamp(Instant.parse(timestamp));
        return appliedTransaction;
    }
}

