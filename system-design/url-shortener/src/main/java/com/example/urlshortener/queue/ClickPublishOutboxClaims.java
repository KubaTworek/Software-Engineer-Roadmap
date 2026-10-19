package com.example.urlshortener.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the database-side delivery lease for durable click publication.
 *
 * <p>The lease prevents two relay instances from publishing the same row at the
 * same time. It does not promise exactly-once delivery: a process can still die
 * after broker ACK and before {@link #published(String)}. The consumer therefore
 * remains idempotent by {@code event_id}.</p>
 */
@Service
public class ClickPublishOutboxClaims {
    private final ClickPublishOutboxRepository repository;
    private final Clock clock;
    private final Duration lease;
    private final int batchSize;

    public ClickPublishOutboxClaims(ClickPublishOutboxRepository repository, Clock clock,
            @Value("${app.queue.publish-outbox-lease:PT30S}") Duration lease,
            @Value("${app.queue.publish-outbox-batch-size:100}") int batchSize) {
        this.repository = repository;
        this.clock = clock;
        this.lease = lease;
        this.batchSize = batchSize;
    }

    @Transactional
    public List<ClickPublishOutboxMessage> claim() {
        Instant now = Instant.now(clock);
        List<ClickPublishOutboxMessage> rows = repository.lockClaimable(now, batchSize);
        rows.forEach(row -> row.claim(now.plus(lease)));
        return List.copyOf(rows);
    }

    @Transactional
    public void published(String eventId) {
        repository.findById(eventId).ifPresent(row -> row.published(Instant.now(clock)));
    }

    @Transactional
    public void retry(String eventId, Duration delay, String error) {
        repository.findById(eventId).ifPresent(row -> row.retryAt(Instant.now(clock).plus(delay), error));
    }

    @Transactional
    public void deadLetter(String eventId, String error) {
        repository.findById(eventId).ifPresent(row -> row.deadLetter(Instant.now(clock), error));
    }
}
