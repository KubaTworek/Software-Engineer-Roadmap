package pl.jakubtworek.marketplace.integration.kafka;

import java.util.Optional;
import java.util.UUID;

public interface ProcessedEventRepository {
    boolean exists(UUID eventId, String consumerName);
    Optional<ProcessedEvent> find(UUID eventId, String consumerName);
    void save(ProcessedEvent processedEvent);
}
