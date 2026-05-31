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

/**
 * Serwis odpowiedzialny za edukacyjne użycie cache/key-value store.
 *
 * Ten serwis należy do etapu NoSQL/cache.
 *
 * Pokazuje kilka typowych zastosowań Redis albo innego key-value store:
 *
 * - cache detali eventu,
 * - cache snapshotu dostępności,
 * - tymczasowe holdy rezerwacyjne z TTL,
 * - prosty rate limiting.
 *
 * Ważne:
 * cache nie jest źródłem prawdy. Źródłem prawdy nadal pozostaje PostgreSQL.
 *
 * Decyzja o faktycznej rezerwacji miejsca powinna przechodzić przez
 * ReservationService i atomowy update w CapacityPoolRepository.
 */
@Service
public class NoSqlCacheService {

    /**
     * TTL dla cache detali eventu.
     *
     * Detale eventu zmieniają się raczej rzadko, więc mogą mieć dłuższy TTL.
     * Tutaj ustawiono 5 minut.
     */
    private static final Duration EVENT_DETAILS_TTL = Duration.ofMinutes(5);

    /**
     * TTL dla snapshotu dostępności.
     *
     * Dostępność zmienia się często, więc TTL powinien być krótki.
     *
     * Nawet 15 sekund może być dużo przy dużym ruchu.
     * Dlatego ten snapshot nadaje się do wyświetlania, ale nie do podejmowania
     * decyzji o sprzedaży miejsca.
     */
    private static final Duration AVAILABILITY_TTL = Duration.ofSeconds(15);

    /**
     * TTL dla tymczasowego holda rezerwacyjnego.
     *
     * Po 10 minutach hold powinien wygasnąć automatycznie.
     *
     * To pokazuje jedną z mocnych stron Redis/key-value store:
     * dane tymczasowe mogą znikać bez ręcznego joba sprzątającego.
     */
    private static final Duration RESERVATION_HOLD_TTL = Duration.ofMinutes(10);

    /**
     * Długość okna rate limitingu.
     *
     * W tym przykładzie klient ma określoną liczbę tokenów na minutę.
     */
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    /**
     * Maksymalna liczba requestów/tokenów w jednym oknie rate limitingu.
     */
    private static final int RATE_LIMIT_MAX_TOKENS = 5;

    /**
     * Główny serwis eventów.
     *
     * Używany jako źródło danych, gdy cache nie ma wpisu.
     * EventService pobiera dane z PostgreSQL.
     */
    private final EventService eventService;

    /**
     * Cache detali eventu.
     *
     * Implementacja może być:
     * - in-memory w testach,
     * - Redis w profilu realnym.
     */
    private final EventDetailsCache eventDetailsCache;

    /**
     * Cache snapshotu dostępności.
     *
     * Przechowuje krótkotrwały widok dostępności miejsc.
     */
    private final AvailabilitySnapshotCache availabilitySnapshotCache;

    /**
     * Store dla tymczasowych holdów rezerwacyjnych.
     *
     * Holdy są dobrym przykładem danych z TTL:
     * istnieją przez krótki czas i potem wygasają.
     */
    private final ReservationHoldStore reservationHoldStore;

    /**
     * Store dla prostego rate limitingu.
     *
     * Redis dobrze pasuje do rate limitingu, bo operacje na licznikach są szybkie,
     * a klucze mogą mieć TTL.
     */
    private final RateLimiterStore rateLimiterStore;

    /**
     * Constructor injection.
     *
     * Serwis zależy od abstrakcji cache/store, a nie bezpośrednio od Redis.
     * Dzięki temu testy mogą używać implementacji in-memory.
     */
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

    /**
     * Pobiera detale eventu z cache albo z PostgreSQL.
     *
     * To jest klasyczny wzorzec cache-aside:
     *
     * 1. Spróbuj odczytać wpis z cache.
     * 2. Jeśli wpis istnieje, zwróć go.
     * 3. Jeśli wpis nie istnieje, pobierz dane z SQL.
     * 4. Zapisz wynik do cache.
     * 5. Zwróć odpowiedź.
     *
     * Response zawiera source = "CACHE" albo source = "SQL", żeby edukacyjnie
     * było widać, skąd przyszły dane.
     */
    @Transactional(readOnly = true)
    public EventCacheResponse getEventDetails(UUID eventId) {
        return eventDetailsCache.get(eventId)
                .map(entry -> toEventResponse(entry, "CACHE"))
                .orElseGet(() -> loadEventDetailsFromSql(eventId));
    }

    /**
     * Pobiera snapshot dostępności z cache albo z PostgreSQL.
     *
     * Ten snapshot może być nieaktualny, dlatego nie wolno używać go jako
     * ostatecznego warunku przy tworzeniu rezerwacji.
     *
     * Jest dobry do szybkiego odczytu UI, np. "zostało 8 miejsc".
     */
    @Transactional(readOnly = true)
    public AvailabilitySnapshotResponse getAvailabilitySnapshot(UUID eventId) {
        return availabilitySnapshotCache.get(eventId)
                .map(snapshot -> toAvailabilityResponse(snapshot, "CACHE"))
                .orElseGet(() -> loadAvailabilitySnapshotFromSql(eventId));
    }

    /**
     * Tworzy tymczasowy hold rezerwacyjny.
     *
     * Hold zapisuje:
     * - eventId,
     * - customerEmail,
     * - czas utworzenia,
     * - czas wygaśnięcia.
     *
     * W tej wersji projektu hold jest edukacyjny.
     * Nie zastępuje atomowego zmniejszenia dostępności w PostgreSQL.
     */
    public ReservationHoldResponse createTemporaryHold(UUID eventId, String customerEmail) {
        ReservationHold hold = reservationHoldStore.create(eventId, customerEmail, RESERVATION_HOLD_TTL);
        return toHoldResponse(hold);
    }

    /**
     * Pobiera tymczasowy hold.
     *
     * Jeśli hold nie istnieje albo wygasł, store zwraca Optional.empty().
     * Wtedy serwis rzuca NotFoundException.
     */
    public ReservationHoldResponse getTemporaryHold(UUID holdId) {
        ReservationHold hold = reservationHoldStore.find(holdId)
                .orElseThrow(() -> new pl.jakubtworek.booking.exception.NotFoundException(
                        "Reservation hold not found or expired: " + holdId
                ));

        return toHoldResponse(hold);
    }

    /**
     * Zużywa jeden token rate limitu dla danego clientKey.
     *
     * clientKey może oznaczać np.:
     * - IP klienta,
     * - ID użytkownika,
     * - API key,
     * - userId + nazwa endpointu.
     *
     * Store decyduje, czy request mieści się w limicie.
     */
    public RateLimitResponse consumeRateLimitToken(String clientKey) {
        RateLimitDecision decision = rateLimiterStore.consume(
                clientKey,
                RATE_LIMIT_MAX_TOKENS,
                RATE_LIMIT_WINDOW
        );

        return new RateLimitResponse(
                clientKey,
                decision.allowed(),
                decision.remainingTokens(),
                decision.resetAt()
        );
    }

    /**
     * Usuwa cache powiązany z eventem.
     *
     * Powinno być wywoływane po zmianach, które wpływają na widok eventu albo
     * dostępność, np.:
     *
     * - utworzenie rezerwacji,
     * - anulowanie rezerwacji,
     * - zmiana danych eventu.
     */
    public void evictEventCaches(UUID eventId) {
        eventDetailsCache.evict(eventId);
        availabilitySnapshotCache.evict(eventId);
    }

    /**
     * Ładuje detale eventu z PostgreSQL i zapisuje je do cache.
     *
     * To jest ścieżka cache miss.
     *
     * EventService.get(...) pobiera dane z relacyjnej bazy danych.
     * Następnie tworzymy EventCacheEntry z czasem cache'owania i wygaśnięcia.
     */
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

    /**
     * Ładuje snapshot dostępności z PostgreSQL i zapisuje go do cache.
     *
     * TTL jest krótszy niż dla detali eventu, ponieważ dostępność zmienia się
     * znacznie częściej.
     */
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

    /**
     * Mapuje wpis cache detali eventu na DTO odpowiedzi.
     *
     * source informuje, czy dane przyszły z cache, czy z SQL.
     */
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

    /**
     * Mapuje snapshot dostępności na DTO odpowiedzi.
     *
     * Podobnie jak wyżej, source pokazuje, czy dane pochodzą z cache, czy z SQL.
     */
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

    /**
     * Mapuje hold rezerwacyjny na DTO odpowiedzi.
     *
     * activeAt(Instant.now()) mówi, czy hold jest nadal aktywny w chwili odczytu.
     */
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