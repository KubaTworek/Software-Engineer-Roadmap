package pl.jakubtworek.booking.readmodel;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!nosql-real")
public class InMemoryEventSearchReadModelStore implements EventSearchReadModelStore {
    private final ConcurrentHashMap<UUID, EventSearchDocument> documents = new ConcurrentHashMap<>();

    @Override
    public EventSearchDocument save(EventSearchDocument document) {
        documents.put(document.getEventId(), document);
        return document;
    }

    @Override
    public Optional<EventSearchDocument> findByEventId(UUID eventId) {
        return Optional.ofNullable(documents.get(eventId));
    }

    @Override
    public List<EventSearchDocument> search(String city, String category, int limit) {
        return documents.values().stream()
                .filter(document -> document.getCity().equals(city))
                .filter(document -> document.getCategory().equals(category))
                .sorted(Comparator.comparing(EventSearchDocument::getStartsAt))
                .limit(limit)
                .toList();
    }

    @Override
    public void deleteAll() {
        documents.clear();
    }
}
