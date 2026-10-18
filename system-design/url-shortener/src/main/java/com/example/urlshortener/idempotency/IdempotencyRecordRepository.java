package com.example.urlshortener.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    @Transactional
    long deleteByExpiresAtBefore(Instant now);
}
