package pl.jakubtworek.booking.readmodel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventSearchReadModelStore {
    EventSearchDocument save(EventSearchDocument document);
    Optional<EventSearchDocument> findByEventId(UUID eventId);
    List<EventSearchDocument> search(String city, String category, int limit);
    void deleteAll();
}
