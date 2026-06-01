package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.integration.kafka.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryProcessedEventRepository implements ProcessedEventRepository {
    private final Map<String, ProcessedEvent> events = new ConcurrentHashMap<>();

    @Override
    public boolean exists(UUID eventId, String consumerName) {
        return events.containsKey(key(eventId, consumerName));
    }

    @Override
    public Optional<ProcessedEvent> find(UUID eventId, String consumerName) {
        return Optional.ofNullable(events.get(key(eventId, consumerName)));
    }

    @Override
    public void save(ProcessedEvent processedEvent) {
        events.put(key(processedEvent.eventId(), processedEvent.consumerName()), processedEvent);
    }

    private String key(UUID eventId, String consumerName) {
        return eventId + "::" + consumerName;
    }
}
