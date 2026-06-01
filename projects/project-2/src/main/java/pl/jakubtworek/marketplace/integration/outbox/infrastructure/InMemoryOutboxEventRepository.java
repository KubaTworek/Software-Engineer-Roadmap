package pl.jakubtworek.marketplace.integration.outbox.infrastructure;

import pl.jakubtworek.marketplace.integration.outbox.OutboxEvent;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOutboxEventRepository implements OutboxEventRepository {
    private final Map<UUID, OutboxEvent> events = new ConcurrentHashMap<>();

    @Override
    public void save(OutboxEvent event) {
        events.put(event.id(), event);
    }

    @Override
    public Optional<OutboxEvent> findById(UUID eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    @Override
    public List<OutboxEvent> findAll(int limit) {
        return events.values().stream()
                .sorted(Comparator.comparing(OutboxEvent::createdAt))
                .limit(limit)
                .toList();
    }

    @Override
    public List<OutboxEvent> findByStatus(OutboxEventStatus status, int limit) {
        return events.values().stream()
                .filter(event -> event.status() == status)
                .sorted(Comparator.comparing(OutboxEvent::createdAt))
                .limit(limit)
                .toList();
    }

    @Override
    public void markPublished(UUID eventId) {
        events.computeIfPresent(eventId, (id, event) -> event.markPublished(java.time.Instant.now()));
    }

    @Override
    public void markFailed(UUID eventId, String reason) {
        events.computeIfPresent(eventId, (id, event) -> event.markFailed(reason));
    }

    @Override
    public void markNewForRetry(UUID eventId) {
        events.computeIfPresent(eventId, (id, event) -> event.markNewForRetry());
    }
}
