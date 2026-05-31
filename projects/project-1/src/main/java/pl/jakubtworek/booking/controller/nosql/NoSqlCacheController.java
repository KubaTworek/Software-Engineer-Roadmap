package pl.jakubtworek.booking.controller.nosql;

import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.nosql.AvailabilitySnapshotResponse;
import pl.jakubtworek.booking.dto.nosql.EventCacheResponse;
import pl.jakubtworek.booking.dto.nosql.RateLimitResponse;
import pl.jakubtworek.booking.dto.nosql.ReservationHoldResponse;
import pl.jakubtworek.booking.service.NoSqlCacheService;

import java.util.UUID;

/**
 * Kontroler REST pokazujący użycie Redis/cache w etapie NoSQL i cache.
 *
 * Ten kontroler ma charakter edukacyjny.
 *
 * Pokazuje kilka typowych zastosowań key-value store:
 *
 * - cache detali eventu,
 * - cache snapshotu dostępności,
 * - tymczasowe holdy rezerwacyjne z TTL,
 * - prosty rate limiting.
 *
 * Ważne:
 * Redis/cache nie jest źródłem prawdy dla rezerwacji.
 * Źródłem prawdy nadal pozostaje PostgreSQL.
 *
 * Dlatego krytyczna decyzja typu "czy można sprzedać miejsce" nie powinna opierać
 * się wyłącznie na danych z cache. Musi przejść przez relacyjną bazę i atomowy
 * update dostępności.
 */
@RestController
@RequestMapping("/api/nosql/cache")
public class NoSqlCacheController {

    /**
     * Serwis obsługujący operacje cache.
     *
     * Kontroler nie powinien znać szczegółów Redis, TTL, kluczy cache ani sposobu
     * serializacji danych. Te szczegóły są ukryte w NoSqlCacheService i niższych
     * abstrakcjach cache.
     */
    private final NoSqlCacheService noSqlCacheService;

    /**
     * Constructor injection.
     *
     * Dzięki temu zależność jest jawna, pole może być final, a kontroler jest
     * prosty do przetestowania.
     */
    public NoSqlCacheController(NoSqlCacheService noSqlCacheService) {
        this.noSqlCacheService = noSqlCacheService;
    }

    /**
     * Pobiera detale eventu z cache albo buduje wpis cache na podstawie PostgreSQL.
     *
     * Endpoint:
     *
     * GET /api/nosql/cache/events/{eventId}
     *
     * Typowy wzorzec to cache-aside:
     *
     * 1. Spróbuj odczytać event z cache.
     * 2. Jeśli wpis istnieje — zwróć go.
     * 3. Jeśli wpis nie istnieje — pobierz dane z PostgreSQL.
     * 4. Zapisz wynik do cache.
     * 5. Zwróć odpowiedź.
     *
     * Response może zawierać informację, czy był cache hit czy cache miss.
     */
    @GetMapping("/events/{eventId}")
    public EventCacheResponse getEventDetails(@PathVariable UUID eventId) {
        return noSqlCacheService.getEventDetails(eventId);
    }

    /**
     * Pobiera snapshot dostępności eventu.
     *
     * Endpoint:
     *
     * GET /api/nosql/cache/events/{eventId}/availability
     *
     * Snapshot dostępności jest przydatny do szybkiego wyświetlania informacji
     * użytkownikowi, np. "zostało 12 miejsc".
     *
     * Ale taki snapshot może być chwilowo nieaktualny.
     *
     * Dlatego nie wolno traktować go jako ostatecznego źródła decyzji przy
     * tworzeniu rezerwacji. Rezerwacja musi nadal użyć atomowego update'u
     * w PostgreSQL.
     */
    @GetMapping("/events/{eventId}/availability")
    public AvailabilitySnapshotResponse getAvailabilitySnapshot(@PathVariable UUID eventId) {
        return noSqlCacheService.getAvailabilitySnapshot(eventId);
    }

    /**
     * Ręcznie usuwa wpisy cache powiązane z eventem.
     *
     * Endpoint:
     *
     * DELETE /api/nosql/cache/events/{eventId}
     *
     * To pokazuje cache invalidation.
     *
     * Po zmianach takich jak:
     * - utworzenie rezerwacji,
     * - anulowanie rezerwacji,
     * - zmiana danych eventu,
     *
     * stare dane w cache mogą być niepoprawne, więc trzeba je usunąć albo
     * odświeżyć.
     *
     * Produkcyjnie taki endpoint powinien być zabezpieczony albo dostępny tylko
     * administracyjnie.
     */
    @DeleteMapping("/events/{eventId}")
    public void evictEventCaches(@PathVariable UUID eventId) {
        noSqlCacheService.evictEventCaches(eventId);
    }

    /**
     * Tworzy tymczasowy hold rezerwacyjny z TTL.
     *
     * Endpoint:
     *
     * POST /api/nosql/cache/reservation-holds?eventId=...&customerEmail=...
     *
     * Hold z TTL pokazuje typowe zastosowanie Redis:
     *
     * - wpis istnieje tylko przez określony czas,
     * - po czasie automatycznie wygasa,
     * - nie trzeba ręcznie sprzątać starych wpisów.
     *
     * Ważna uwaga:
     * w tej wersji projektu hold ma charakter edukacyjny.
     * Nie powinien być traktowany jako pełny mechanizm blokowania miejsca,
     * jeśli finalna rezerwacja nadal odbywa się przez PostgreSQL.
     */
    @PostMapping("/reservation-holds")
    public ReservationHoldResponse createReservationHold(@RequestParam UUID eventId,
                                                         @RequestParam String customerEmail) {
        return noSqlCacheService.createTemporaryHold(eventId, customerEmail);
    }

    /**
     * Pobiera tymczasowy hold rezerwacyjny.
     *
     * Endpoint:
     *
     * GET /api/nosql/cache/reservation-holds/{holdId}
     *
     * Jeśli hold wygasł przez TTL, serwis powinien zwrócić błąd typu 404
     * albo odpowiedź informującą, że hold już nie istnieje.
     *
     * To dobrze pokazuje naturę danych w cache:
     * dane mogą zniknąć automatycznie po czasie.
     */
    @GetMapping("/reservation-holds/{holdId}")
    public ReservationHoldResponse getReservationHold(@PathVariable UUID holdId) {
        return noSqlCacheService.getTemporaryHold(holdId);
    }

    /**
     * Zużywa jeden token rate limitu dla danego klienta.
     *
     * Endpoint:
     *
     * POST /api/nosql/cache/rate-limit/{clientKey}
     *
     * clientKey może reprezentować np.:
     * - adres IP,
     * - ID użytkownika,
     * - klucz API,
     * - kombinację userId + endpoint.
     *
     * Redis dobrze pasuje do prostego rate limitingu, bo oferuje szybkie operacje
     * na licznikach oraz TTL.
     *
     * Przykładowy model:
     *
     * - pierwszy request tworzy licznik z TTL,
     * - każdy kolejny request zwiększa licznik,
     * - po przekroczeniu limitu request powinien zostać odrzucony,
     * - po wygaśnięciu TTL okno limitu zaczyna się od nowa.
     */
    @PostMapping("/rate-limit/{clientKey}")
    public RateLimitResponse consumeRateLimitToken(@PathVariable String clientKey) {
        return noSqlCacheService.consumeRateLimitToken(clientKey);
    }
}