package pl.jakubtworek.booking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.EventRepository;

import java.util.UUID;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final CapacityPoolRepository capacityPoolRepository;

    public EventService(EventRepository eventRepository, CapacityPoolRepository capacityPoolRepository) {
        this.eventRepository = eventRepository;
        this.capacityPoolRepository = capacityPoolRepository;
    }

    @Transactional
    public EventResponse create(EventCreateRequest request) {
        Event event = eventRepository.save(new Event(
                request.name(),
                request.city(),
                request.category(),
                request.startsAt()
        ));
        CapacityPool pool = capacityPoolRepository.save(new CapacityPool(event, request.totalCapacity()));
        return toResponse(event, pool);
    }

    @Transactional(readOnly = true)
    public EventResponse get(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
        CapacityPool pool = capacityPoolRepository.findByEventId(eventId)
                .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));
        return toResponse(event, pool);
    }

    private EventResponse toResponse(Event event, CapacityPool pool) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getCity(),
                event.getCategory(),
                event.getStartsAt(),
                event.getStatus(),
                pool.getTotalCapacity(),
                pool.getAvailableCapacity()
        );
    }
}
