package com.charlesSchwab.account_service.entity;

import com.charlesSchwab.account_service.enums.TransactionType;
import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "applied_transaction")
public class AppliedTransaction {
    @Id
    private String eventId;
    private String accountId;
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    @Column(precision = 19, scale = 4)
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
}