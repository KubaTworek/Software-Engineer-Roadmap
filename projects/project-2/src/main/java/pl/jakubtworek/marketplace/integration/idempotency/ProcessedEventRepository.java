package pl.jakubtworek.marketplace.integration.idempotency;

import java.util.UUID;

public interface ProcessedEventRepository {
    boolean alreadyProcessed(UUID eventId, String consumerName);
    void markProcessed(UUID eventId, String consumerName);
}
