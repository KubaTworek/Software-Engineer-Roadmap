package pl.jakubtworek.backend.catalog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.jakubtworek.backend.catalog.api.AvailabilityResponse;
import pl.jakubtworek.backend.catalog.api.EventResponse;
import pl.jakubtworek.backend.catalog.cache.RedisJsonCache;
import pl.jakubtworek.backend.catalog.domain.EventEntity;
import pl.jakubtworek.backend.catalog.repository.EventRepository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class EventCatalogService {
    private final EventRepository eventRepository;
    private final RedisJsonCache cache;
    private final ObjectMapper objectMapper;
    private final Duration eventListTtl;
    private final Duration eventDetailsTtl;
    private final Duration availabilityTtl;

    public EventCatalogService(EventRepository eventRepository,
                               RedisJsonCache cache,
                               ObjectMapper objectMapper,
                               @Value("${app.cache.events.ttl:60s}") Duration eventListTtl,
                               @Value("${app.cache.event-details.ttl:120s}") Duration eventDetailsTtl,
                               @Value("${app.cache.availability.ttl:10s}") Duration availabilityTtl) {
        this.eventRepository = eventRepository;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.eventListTtl = eventListTtl;
        this.eventDetailsTtl = eventDetailsTtl;
        this.availabilityTtl = availabilityTtl;
    }

    public List<EventResponse> listEvents() {
        return cache.getOrLoad(
                "events",
                "all",
                eventListTtl,
                objectMapper.getTypeFactory().constructCollectionType(List.class, EventResponse.class),
                () -> eventRepository.findAll().stream().map(this::toResponse).toList()
        );
    }

    public EventResponse getEvent(UUID id) {
        return cache.getOrLoad(
                "event-details",
                id.toString(),
                eventDetailsTtl,
                objectMapper.getTypeFactory().constructType(EventResponse.class),
                () -> eventRepository.findById(id).map(this::toResponse)
                        .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id))
        );
    }

    public AvailabilityResponse getAvailability(UUID id) {
        return cache.getOrLoad(
                "event-availability",
                id.toString(),
                availabilityTtl,
                objectMapper.getTypeFactory().constructType(AvailabilityResponse.class),
                () -> {
                    EventEntity event = eventRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));
                    return new AvailabilityResponse(event.getId(), event.getAvailableTickets());
                }
        );
    }

    private EventResponse toResponse(EventEntity event) {
        return new EventResponse(event.getId(), event.getName(), event.getVenue(), event.getStartsAt(), event.getTotalTickets(), event.getAvailableTickets());
    }
}
