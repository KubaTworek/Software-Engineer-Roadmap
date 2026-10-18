package pl.jakubtworek.chatsystem.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Commit claimu musi poprzedzać publikację do zewnętrznego brokera. W przeciwnym
 * razie bardzo szybki consumer może ścigać się z niezatwierdzoną transakcją outboxa.
 */
@Service
public class OutboxClaimService {
    private final OutboxEventRepository repository;
    public OutboxClaimService(OutboxEventRepository repository) { this.repository = repository; }

    @Transactional
    public List<OutboxEvent> claim(int maxAttempts, int batchSize, String workerId) {
        repository.findExpiredClaims(Instant.now().minusSeconds(30)).forEach(OutboxEvent::recoverExpiredClaim);
        List<OutboxEvent> events = repository.lockPublishable(maxAttempts, batchSize);
        events.forEach(event -> event.markEnqueued(workerId));
        return repository.saveAll(events);
    }

    @Transactional
    public void publicationFailed(UUID eventId, String error) {
        repository.findById(eventId).ifPresent(event -> {
            event.markFailed(error);
            repository.save(event);
        });
    }
}
