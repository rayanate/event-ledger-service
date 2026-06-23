package com.charlesSchwab.event_gateway.entity;

import com.charlesSchwab.event_gateway.model.TransactionType;
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
@Table(name = "event_record")
public class EventRecord {
    @Id                                  // eventId as PK = idempotency key
    private String eventId;
    private String accountId;
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    @Column(precision = 19, scale = 4)
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
    @Column(length = 2000)
    private String metadata;             // raw JSON stored as a string
    private Instant receivedAt;          // when the Gateway accepted it
}