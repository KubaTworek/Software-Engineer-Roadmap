package pl.jakubtworek.booking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.nosql.EventSearchDocumentListResponse;
import pl.jakubtworek.booking.dto.nosql.EventSearchDocumentResponse;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Organization;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.readmodel.EventSearchDocument;
import pl.jakubtworek.booking.readmodel.EventSearchReadModelStore;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EventSearchReadModelService {
    private final EventRepository eventRepository;
    private final CapacityPoolRepository capacityPoolRepository;
    private final ReservationRepository reservationRepository;
    private final EventSearchReadModelStore store;

    public EventSearchReadModelService(EventRepository eventRepository,
                                       CapacityPoolRepository capacityPoolRepository,
                                       ReservationRepository reservationRepository,
                                       EventSearchReadModelStore store) {
        this.eventRepository = eventRepository;
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationRepository = reservationRepository;
        this.store = store;
    }

    @Transactional(readOnly = true)
    public EventSearchDocumentResponse rebuildOne(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
        CapacityPool pool = capacityPoolRepository.findByEventId(eventId)
                .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));

        Map<String, Long> reservationsByStatus = new LinkedHashMap<>();
        for (Object[] row : reservationRepository.countByStatusForEvent(eventId)) {
            reservationsByStatus.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Organization organization = event.getOrganization();
        EventSearchDocument document = new EventSearchDocument(
                event.getId(),
                event.getName(),
                event.getCity(),
                event.getCategory(),
                event.getStartsAt(),
                organization == null ? null : organization.getId(),
                organization == null ? null : organization.getName(),
                pool.getTotalCapacity(),
                pool.getAvailableCapacity(),
                reservationsByStatus,
                Instant.now()
        );
        return toResponse(store.save(document));
    }

    public EventSearchDocumentResponse get(UUID eventId) {
        return store.findByEventId(eventId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Event search document not found: " + eventId));
    }

    public EventSearchDocumentListResponse search(String city, String category, int limit) {
        var items = store.search(city, category, limit).stream()
                .map(this::toResponse)
                .toList();
        return new EventSearchDocumentListResponse(items.size(), items);
    }

    public void clear() {
        store.deleteAll();
    }

    private EventSearchDocumentResponse toResponse(EventSearchDocument document) {
        return new EventSearchDocumentResponse(
                document.getEventId(),
                document.getName(),
                document.getCity(),
                document.getCategory(),
                document.getStartsAt(),
                document.getOrganizationId(),
                document.getOrganizationName(),
                document.getTotalCapacity(),
                document.getAvailableCapacity(),
                document.getReservationsByStatus(),
                document.getRebuiltAt()
        );
    }
}
