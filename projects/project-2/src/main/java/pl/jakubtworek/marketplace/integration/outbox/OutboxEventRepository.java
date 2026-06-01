package pl.jakubtworek.marketplace.integration.outbox;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {
    void save(OutboxEvent event);
    List<OutboxEvent> findNew(int limit);
    void markPublished(UUID eventId);
    void markFailed(UUID eventId, String reason);
}
