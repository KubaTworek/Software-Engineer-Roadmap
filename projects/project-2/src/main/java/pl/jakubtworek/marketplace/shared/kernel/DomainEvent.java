package pl.jakubtworek.marketplace.shared.kernel;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();
    UUID aggregateId();
    String eventType();
    int eventVersion();
    Instant occurredAt();
    UUID correlationId();
    UUID causationId();
}
