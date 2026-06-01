package pl.jakubtworek.marketplace.integration.outbox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {
    void save(OutboxEvent event);
    Optional<OutboxEvent> findById(UUID eventId);
    List<OutboxEvent> findAll(int limit);
    List<OutboxEvent> findByStatus(OutboxEventStatus status, int limit);

    default List<OutboxEvent> findNew(int limit) {
        return findByStatus(OutboxEventStatus.NEW, limit);
    }

    default List<OutboxEvent> findFailed(int limit) {
        return findByStatus(OutboxEventStatus.FAILED, limit);
    }

    void markPublished(UUID eventId);
    void markFailed(UUID eventId, String reason);
    void markNewForRetry(UUID eventId);
}
