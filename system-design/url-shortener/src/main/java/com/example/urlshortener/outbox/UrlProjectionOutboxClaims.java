package com.example.urlshortener.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claims a disjoint batch so multiple relay instances cannot process the same row concurrently. */
@Service
public class UrlProjectionOutboxClaims {
    private final UrlProjectionOutboxRepository repository;
    private final Clock clock;
    private final Duration lease;
    private final int batchSize;

    public UrlProjectionOutboxClaims(UrlProjectionOutboxRepository repository, Clock clock,
            @Value("${app.projection-outbox.lease:PT30S}") Duration lease,
            @Value("${app.projection-outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.clock = clock;
        this.lease = lease;
        this.batchSize = batchSize;
    }

    @Transactional
    public List<UrlProjectionOutboxMessage> claim() {
        Instant now = Instant.now(clock);
        List<UrlProjectionOutboxMessage> messages = repository.lockClaimable(now, batchSize);
        messages.forEach(message -> message.claim(now.plus(lease)));
        return List.copyOf(messages);
    }

    @Transactional
    public void retry(String eventId, Duration delay, String error) {
        repository.findById(eventId).ifPresent(message -> message.retryAt(Instant.now(clock).plus(delay), error));
    }

    @Transactional
    public void deadLetter(String eventId, String error) {
        repository.findById(eventId).ifPresent(message -> message.deadLetter(Instant.now(clock), error));
    }
}
