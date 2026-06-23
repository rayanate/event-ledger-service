package com.charlesSchwab.account_service.repository;

import com.charlesSchwab.account_service.entity.AppliedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppliedTransactionRepository extends JpaRepository<AppliedTransaction, String> {
    List<AppliedTransaction> findByAccountId(String accountId);
}