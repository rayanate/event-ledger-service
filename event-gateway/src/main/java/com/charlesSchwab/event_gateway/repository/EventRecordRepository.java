package com.charlesSchwab.event_gateway.repository;

import com.charlesSchwab.event_gateway.entity.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRecordRepository extends JpaRepository<EventRecord, String> {
    List<EventRecord> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
