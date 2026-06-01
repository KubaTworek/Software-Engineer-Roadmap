package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.integration.kafka.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryDlqEventRepository implements DlqEventRepository {
    private final ConcurrentHashMap<UUID, DlqEvent> events = new ConcurrentHashMap<>();

    @Override
    public void save(DlqEvent event) {
        events.put(event.id(), event);
    }

    @Override
    public Optional<DlqEvent> findById(UUID id) {
        return Optional.ofNullable(events.get(id));
    }

    @Override
    public List<DlqEvent> findByStatus(DlqEventStatus status, int limit) {
        return events.values().stream()
                .filter(event -> event.status() == status)
                .sorted(Comparator.comparing(DlqEvent::failedAt))
                .limit(limit)
                .toList();
    }

    @Override
    public List<DlqEvent> findAll() {
        return events.values().stream()
                .sorted(Comparator.comparing(DlqEvent::failedAt))
                .toList();
    }
}
