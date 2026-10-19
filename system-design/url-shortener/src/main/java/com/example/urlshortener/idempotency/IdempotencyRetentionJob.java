package com.example.urlshortener.idempotency;

import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expired command results are operational state, not permanent business data. */
@Component
public class IdempotencyRetentionJob {
    private final IdempotencyRecordRepository repository;
    private final Clock clock;

    public IdempotencyRetentionJob(IdempotencyRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-delay:PT1H}")
    public void deleteExpired() {
        repository.deleteByExpiresAtBefore(Instant.now(clock));
    }
}
