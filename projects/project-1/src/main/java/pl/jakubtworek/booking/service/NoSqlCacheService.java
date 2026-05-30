package pl.jakubtworek.booking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.cache.*;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.dto.nosql.AvailabilitySnapshotResponse;
import pl.jakubtworek.booking.dto.nosql.EventCacheResponse;
import pl.jakubtworek.booking.dto.nosql.RateLimitResponse;
import pl.jakubtworek.booking.dto.nosql.ReservationHoldResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class NoSqlCacheService {
    private static final Duration EVENT_DETAILS_TTL = Duration.ofMinutes(5);
    private static final Duration AVAILABILITY_TTL = Duration.ofSeconds(15);
    private static final Duration RESERVATION_HOLD_TTL = Duration.ofMinutes(10);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int RATE_LIMIT_MAX_TOKENS = 5;

    private final EventService eventService;
    private final EventDetailsCache eventDetailsCache;
    private final AvailabilitySnapshotCache availabilitySnapshotCache;
    private final ReservationHoldStore reservationHoldStore;
    private final RateLimiterStore rateLimiterStore;

    public NoSqlCacheService(
            EventService eventService,
            EventDetailsCache eventDetailsCache,
            AvailabilitySnapshotCache availabilitySnapshotCache,
            ReservationHoldStore reservationHoldStore,
            RateLimiterStore rateLimiterStore
    ) {
        this.eventService = eventService;
        this.eventDetailsCache = eventDetailsCache;
        this.availabilitySnapshotCache = availabilitySnapshotCache;
        this.reservationHoldStore = reservationHoldStore;
        this.rateLimiterStore = rateLimiterStore;
    }

    @Transactional(readOnly = true)
    public EventCacheResponse getEventDetails(UUID eventId) {
        return eventDetailsCache.get(eventId)
                .map(entry -> toEventResponse(entry, "CACHE"))
                .orElseGet(() -> loadEventDetailsFromSql(eventId));
    }

    @Transactional(readOnly = true)
    public AvailabilitySnapshotResponse getAvailabilitySnapshot(UUID eventId) {
        return availabilitySnapshotCache.get(eventId)
                .map(snapshot -> toAvailabilityResponse(snapshot, "CACHE"))
                .orElseGet(() -> loadAvailabilitySnapshotFromSql(eventId));
    }

    public ReservationHoldResponse createTemporaryHold(UUID eventId, String customerEmail) {
        ReservationHold hold = reservationHoldStore.create(eventId, customerEmail, RESERVATION_HOLD_TTL);
        return toHoldResponse(hold);
    }

    public ReservationHoldResponse getTemporaryHold(UUID holdId) {
        ReservationHold hold = reservationHoldStore.find(holdId)
                .orElseThrow(() -> new pl.jakubtworek.booking.exception.NotFoundException("Reservation hold not found or expired: " + holdId));
        return toHoldResponse(hold);
    }

    public RateLimitResponse consumeRateLimitToken(String clientKey) {
        RateLimitDecision decision = rateLimiterStore.consume(clientKey, RATE_LIMIT_MAX_TOKENS, RATE_LIMIT_WINDOW);
        return new RateLimitResponse(clientKey, decision.allowed(), decision.remainingTokens(), decision.resetAt());
    }

    public void evictEventCaches(UUID eventId) {
        eventDetailsCache.evict(eventId);
        availabilitySnapshotCache.evict(eventId);
    }

    private EventCacheResponse loadEventDetailsFromSql(UUID eventId) {
        EventResponse event = eventService.get(eventId);
        Instant now = Instant.now();
        EventCacheEntry entry = new EventCacheEntry(
                event.id(),
                event.name(),
                event.city(),
                event.category(),
                event.startsAt(),
                event.totalCapacity(),
                event.availableCapacity(),
                now,
                now.plus(EVENT_DETAILS_TTL)
        );
        eventDetailsCache.put(entry, EVENT_DETAILS_TTL);
        return toEventResponse(entry, "SQL");
    }

    private AvailabilitySnapshotResponse loadAvailabilitySnapshotFromSql(UUID eventId) {
        EventResponse event = eventService.get(eventId);
        Instant now = Instant.now();
        AvailabilitySnapshot snapshot = new AvailabilitySnapshot(
                event.id(),
                event.totalCapacity(),
                event.availableCapacity(),
                now,
                now.plus(AVAILABILITY_TTL)
        );
        availabilitySnapshotCache.put(snapshot, AVAILABILITY_TTL);
        return toAvailabilityResponse(snapshot, "SQL");
    }

    private EventCacheResponse toEventResponse(EventCacheEntry entry, String source) {
        return new EventCacheResponse(
                entry.eventId(),
                entry.name(),
                entry.city(),
                entry.category(),
                entry.startsAt(),
                entry.totalCapacity(),
                entry.availableCapacity(),
                source,
                entry.cachedAt(),
                entry.expiresAt()
        );
    }

    private AvailabilitySnapshotResponse toAvailabilityResponse(AvailabilitySnapshot snapshot, String source) {
        return new AvailabilitySnapshotResponse(
                snapshot.eventId(),
                snapshot.totalCapacity(),
                snapshot.availableCapacity(),
                source,
                snapshot.snapshotAt(),
                snapshot.expiresAt()
        );
    }

    private ReservationHoldResponse toHoldResponse(ReservationHold hold) {
        return new ReservationHoldResponse(
                hold.holdId(),
                hold.eventId(),
                hold.customerEmail(),
                hold.createdAt(),
                hold.expiresAt(),
                hold.activeAt(Instant.now())
        );
    }
}
