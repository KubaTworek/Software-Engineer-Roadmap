package pl.jakubtworek.backend.catalog.application;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import pl.jakubtworek.backend.catalog.api.AvailabilityResponse;
import pl.jakubtworek.backend.catalog.api.EventResponse;
import pl.jakubtworek.backend.catalog.domain.EventEntity;
import pl.jakubtworek.backend.catalog.repository.EventRepository;

import java.util.List;
import java.util.UUID;

@Service
public class EventCatalogService {
    private final EventRepository eventRepository;

    public EventCatalogService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Cacheable(cacheNames = "events")
    public List<EventResponse> listEvents() {
        return eventRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Cacheable(cacheNames = "event-details", key = "#id")
    public EventResponse getEvent(UUID id) {
        return eventRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));
    }

    @Cacheable(cacheNames = "event-availability", key = "#id")
    public AvailabilityResponse getAvailability(UUID id) {
        EventEntity event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));
        return new AvailabilityResponse(event.getId(), event.getAvailableTickets());
    }

    private EventResponse toResponse(EventEntity event) {
        return new EventResponse(event.getId(), event.getName(), event.getVenue(), event.getStartsAt(), event.getTotalTickets(), event.getAvailableTickets());
    }
}
