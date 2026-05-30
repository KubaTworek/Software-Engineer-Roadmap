package pl.jakubtworek.booking.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.*;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.entity.ReservationStatus;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SqlPerformanceService {
    private static final int MAX_PAGE_SIZE = 200;

    private final EventRepository eventRepository;
    private final ReservationRepository reservationRepository;
    private final JdbcTemplate jdbcTemplate;

    public SqlPerformanceService(EventRepository eventRepository,
                                 ReservationRepository reservationRepository,
                                 JdbcTemplate jdbcTemplate) {
        this.eventRepository = eventRepository;
        this.reservationRepository = reservationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<EventSearchResponse> searchEvents(String city, OffsetDateTime from, String category) {
        return eventRepository.searchByCityCategoryAndFrom(city, category, from)
                .stream()
                .map(this::toEventSearchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReservationListItemResponse> organizationReservations(UUID organizationId,
                                                                      ReservationStatus status,
                                                                      int page,
                                                                      int size) {
        return reservationRepository.findOrganizationReservationsByStatus(
                organizationId,
                status,
                PageRequest.of(Math.max(page, 0), limit(size))
        );
    }

    @Transactional(readOnly = true)
    public Page<ReservationListItemResponse> customerReservationsOffset(UUID customerId, int page, int size) {
        return reservationRepository.findCustomerReservationsOffset(
                customerId,
                PageRequest.of(Math.max(page, 0), limit(size))
        );
    }

    @Transactional(readOnly = true)
    public KeysetReservationPageResponse customerReservationsKeyset(UUID customerId,
                                                                    Instant afterCreatedAt,
                                                                    int size) {
        int requested = limit(size);
        List<ReservationListItemResponse> fetched = reservationRepository.findCustomerReservationsKeyset(
                customerId,
                afterCreatedAt,
                PageRequest.of(0, requested + 1)
        );
        boolean hasNext = fetched.size() > requested;
        List<ReservationListItemResponse> items = hasNext ? fetched.subList(0, requested) : fetched;
        ReservationListItemResponse last = items.isEmpty() ? null : items.get(items.size() - 1);
        return new KeysetReservationPageResponse(
                items,
                last == null ? null : last.createdAt(),
                last == null ? null : last.id(),
                hasNext
        );
    }

    @Transactional(readOnly = true)
    public EventStatsResponse eventStats(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Event not found: " + eventId);
        }
        Map<ReservationStatus, Long> counts = new EnumMap<>(ReservationStatus.class);
        for (Object[] row : reservationRepository.countByStatusForEvent(eventId)) {
            counts.put((ReservationStatus) row[0], (Long) row[1]);
        }
        long pending = counts.getOrDefault(ReservationStatus.PENDING, 0L);
        long confirmed = counts.getOrDefault(ReservationStatus.CONFIRMED, 0L);
        long cancelled = counts.getOrDefault(ReservationStatus.CANCELLED, 0L);
        long paymentTimeout = counts.getOrDefault(ReservationStatus.PAYMENT_TIMEOUT, 0L);
        return new EventStatsResponse(eventId, pending + confirmed + cancelled + paymentTimeout,
                pending, confirmed, cancelled, paymentTimeout);
    }

    @Transactional(readOnly = true)
    public List<ReservationListItemResponse> eventReservationsNaiveNPlusOne(UUID eventId) {
        List<Reservation> reservations = reservationRepository.findByEventIdNaive(eventId);
        return reservations.stream().map(this::toReservationListItemResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationListItemResponse> eventReservationsFetchJoin(UUID eventId) {
        List<Reservation> reservations = reservationRepository.findByEventIdUsingFetchJoin(eventId);
        return reservations.stream().map(this::toReservationListItemResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationListItemResponse> eventReservationsEntityGraph(UUID eventId) {
        List<Reservation> reservations = reservationRepository.findByEventIdUsingEntityGraph(eventId);
        return reservations.stream().map(this::toReservationListItemResponse).toList();
    }

    public ExplainAnalyzeResponse explainEventSearch(String city, String category, OffsetDateTime from) {
        List<String> plan = jdbcTemplate.queryForList("""
                EXPLAIN ANALYZE
                SELECT id, name, city, category, starts_at, status
                  FROM events
                 WHERE city = ?
                   AND category = ?
                   AND starts_at >= ?
                 ORDER BY starts_at ASC
                """, String.class, city, category, from);
        return new ExplainAnalyzeResponse("event-search", plan);
    }

    private EventSearchResponse toEventSearchResponse(Event event) {
        return new EventSearchResponse(
                event.getId(),
                event.getName(),
                event.getCity(),
                event.getCategory(),
                event.getStartsAt(),
                event.getStatus()
        );
    }

    private ReservationListItemResponse toReservationListItemResponse(Reservation reservation) {
        return new ReservationListItemResponse(
                reservation.getId(),
                reservation.getEvent().getId(),
                reservation.getEvent().getName(),
                reservation.getCustomer().getId(),
                reservation.getCustomer().getEmail(),
                reservation.getStatus(),
                reservation.getCreatedAt()
        );
    }

    private int limit(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
