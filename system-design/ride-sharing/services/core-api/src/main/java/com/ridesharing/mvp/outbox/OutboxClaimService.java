package com.ridesharing.mvp.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Krótkie transakcje claim/ack dla relaya. Dzięki SKIP LOCKED wiele instancji
 * może pracować równolegle, a żadna nie utrzymuje blokady DB podczas oczekiwania
 * na potwierdzenie brokera.
 */
@Service
@RequiredArgsConstructor
public class OutboxClaimService {
    private final OutboxEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claim(int batchSize) {
        Instant now = Instant.now();
        var events = repository.lockPublishableBatch(now, batchSize);
        events.forEach(event -> {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLockedUntil(now.plusSeconds(30));
        });
        return List.copyOf(events);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void published(UUID eventId) {
        repository.findLockedById(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setLockedUntil(null);
            event.setLastError(null);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(UUID eventId, String error) {
        repository.findLockedById(eventId).ifPresent(event -> {
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(error == null ? "unknown broker error" : error.substring(0, Math.min(error.length(), 2000)));
            event.setLockedUntil(null);
            event.setStatus(event.getAttempts() >= 10 ? OutboxStatus.FAILED : OutboxStatus.PENDING);
        });
    }
}
