package com.example.filestorage.production.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Krótka transakcja claimu pozwala wielu instancjom dzielić outbox. */
@Service
public class OutboxClaims {
    private final OutboxEventRepository events;
    private final Clock clock;

    public OutboxClaims(OutboxEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public List<UUID> claim(int batchSize) {
        var now = clock.instant();
        var batch = events.claimableBatch(now, now.minus(Duration.ofMinutes(2)), batchSize);
        batch.forEach(event -> event.claim(now));
        return batch.stream().map(OutboxEvent::getId).toList();
    }
}
