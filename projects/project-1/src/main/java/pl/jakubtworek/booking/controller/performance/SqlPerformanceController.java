package pl.jakubtworek.booking.controller.performance;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.EventStatsResponse;
import pl.jakubtworek.booking.dto.ExplainAnalyzeResponse;
import pl.jakubtworek.booking.dto.KeysetReservationPageResponse;
import pl.jakubtworek.booking.dto.ReservationListItemResponse;
import pl.jakubtworek.booking.entity.ReservationStatus;
import pl.jakubtworek.booking.service.SqlPerformanceService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Kontroler REST dla etapu SQL i performance.
 *
 * Ten kontroler ma charakter edukacyjny.
 *
 * Pokazuje endpointy, które nadają się do analizy:
 *
 * - indeksów,
 * - planów wykonania,
 * - offset pagination,
 * - keyset pagination,
 * - N+1,
 * - fetch join,
 * - EntityGraph,
 * - agregacji po stronie bazy.
 *
 * W zwykłym produkcyjnym projekcie część tych endpointów mogłaby trafić
 * do osobnych kontrolerów domenowych. Tutaj są zebrane razem, żeby łatwiej
 * testować i porównywać zachowanie SQL.
 */
@RestController
public class SqlPerformanceController {

    /**
     * Serwis zawierający zapytania i scenariusze do analizy SQL/performance.
     *
     * Kontroler nie powinien znać szczegółów JPQL, indeksów ani EXPLAIN ANALYZE.
     * Deleguje wszystko do warstwy serwisowej.
     */
    private final SqlPerformanceService sqlPerformanceService;

    /**
     * Constructor injection.
     */
    public SqlPerformanceController(SqlPerformanceService sqlPerformanceService) {
        this.sqlPerformanceService = sqlPerformanceService;
    }

    /**
     * Pobiera rezerwacje organizacji po statusie przez offset pagination.
     *
     * Endpoint:
     *
     * GET /api/organizations/{organizationId}/reservations?status=CONFIRMED&page=0&size=50
     *
     * Access pattern:
     *
     * - organizationId,
     * - status,
     * - sortowanie po createdAt desc.
     *
     * Ten endpoint jest dobry do testowania indeksu:
     *
     * CREATE INDEX idx_reservation_org_status_created_at
     * ON reservations(organization_id, status, created_at DESC);
     *
     * Zwraca Page, więc Spring Data zwykle wykonuje także zapytanie COUNT.
     * Przy bardzo dużych tabelach COUNT może być osobnym kosztem.
     */
    @GetMapping("/api/organizations/{organizationId}/reservations")
    public Page<ReservationListItemResponse> organizationReservations(
            @PathVariable UUID organizationId,
            @RequestParam ReservationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return sqlPerformanceService.organizationReservations(organizationId, status, page, size);
    }

    /**
     * Pobiera rezerwacje klienta przez offset pagination.
     *
     * Endpoint:
     *
     * GET /api/customers/{customerId}/reservations?page=0&size=50
     *
     * Offset pagination jest prosta dla klienta API, ale przy dużym page może być
     * kosztowna, bo baza musi pominąć wiele rekordów.
     *
     * Ten endpoint warto porównać z wariantem keyset.
     */
    @GetMapping("/api/customers/{customerId}/reservations")
    public Page<ReservationListItemResponse> customerReservationsOffset(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return sqlPerformanceService.customerReservationsOffset(customerId, page, size);
    }

    /**
     * Pobiera rezerwacje klienta przez keyset pagination.
     *
     * Endpoint:
     *
     * GET /api/customers/{customerId}/reservations/keyset?afterCreatedAt=...&afterId=...&size=50
     *
     * Keyset pagination nie pyta o numer strony. Zamiast tego używa kursora
     * ostatniego elementu z poprzedniej strony.
     *
     * Zalety:
     * - stabilniejsza wydajność przy dużych danych,
     * - lepsze wykorzystanie indeksu,
     * - brak kosztownego OFFSET.
     *
     * Uwaga:
     * aktualna metoda przekazuje do serwisu tylko afterCreatedAt, ignorując afterId.
     * Jeśli sortowanie jest po createdAt desc, id desc, pełny cursor powinien
     * zawierać oba pola.
     */
    @GetMapping("/api/customers/{customerId}/reservations/keyset")
    public KeysetReservationPageResponse customerReservationsKeyset(
            @PathVariable UUID customerId,
            @RequestParam(required = false) Instant afterCreatedAt,
            @RequestParam(required = false) UUID afterId,
            @RequestParam(defaultValue = "50") int size
    ) {
        return sqlPerformanceService.customerReservationsKeyset(customerId, afterCreatedAt, size);
    }

    /**
     * Zwraca statystyki rezerwacji dla eventu.
     *
     * Endpoint:
     *
     * GET /api/events/{eventId}/stats
     *
     * Ten endpoint powinien używać agregacji po stronie bazy:
     *
     * SELECT status, COUNT(*)
     * FROM reservations
     * WHERE event_id = ?
     * GROUP BY status
     *
     * To jest lepsze niż pobranie wszystkich rezerwacji do aplikacji
     * i liczenie ich w Javie.
     */
    @GetMapping("/api/events/{eventId}/stats")
    public EventStatsResponse eventStats(@PathVariable UUID eventId) {
        return sqlPerformanceService.eventStats(eventId);
    }

    /**
     * Celowo naiwny endpoint pokazujący problem N+1.
     *
     * Endpoint:
     *
     * GET /api/events/{eventId}/reservations/n-plus-one
     *
     * Ten endpoint pobiera listę rezerwacji, a potem podczas mapowania DTO
     * może dociągać event/customer dodatkowymi zapytaniami.
     *
     * Nie jest to endpoint produkcyjny. Służy do zobaczenia problemu w logach SQL,
     * profilerze albo statystykach Hibernate.
     */
    @GetMapping("/api/events/{eventId}/reservations/n-plus-one")
    public List<ReservationListItemResponse> eventReservationsNaiveNPlusOne(@PathVariable UUID eventId) {
        return sqlPerformanceService.eventReservationsNaiveNPlusOne(eventId);
    }

    /**
     * Poprawiony wariant N+1 przez fetch join.
     *
     * Endpoint:
     *
     * GET /api/events/{eventId}/reservations/fetch-join
     *
     * Fetch join pobiera Reservation razem z potrzebnymi relacjami jednym
     * zapytaniem JPQL.
     */
    @GetMapping("/api/events/{eventId}/reservations/fetch-join")
    public List<ReservationListItemResponse> eventReservationsFetchJoin(@PathVariable UUID eventId) {
        return sqlPerformanceService.eventReservationsFetchJoin(eventId);
    }

    /**
     * Poprawiony wariant N+1 przez EntityGraph.
     *
     * Endpoint:
     *
     * GET /api/events/{eventId}/reservations/entity-graph
     *
     * EntityGraph pozwala wskazać Hibernate, które relacje mają być dociągnięte
     * dla konkretnej metody repozytorium.
     */
    @GetMapping("/api/events/{eventId}/reservations/entity-graph")
    public List<ReservationListItemResponse> eventReservationsEntityGraph(@PathVariable UUID eventId) {
        return sqlPerformanceService.eventReservationsEntityGraph(eventId);
    }

    /**
     * Uruchamia EXPLAIN ANALYZE dla zapytania wyszukiwania eventów.
     *
     * Endpoint:
     *
     * GET /api/performance/explain/event-search?city=Warsaw&category=music&from=2026-06-01T00:00:00Z
     *
     * EXPLAIN ANALYZE pokazuje rzeczywisty plan wykonania i czasy.
     *
     * Ten endpoint jest edukacyjny. W realnej aplikacji nie powinien być publiczny,
     * bo może ujawniać szczegóły bazy danych i generować kosztowne zapytania.
     */
    @GetMapping("/api/performance/explain/event-search")
    public ExplainAnalyzeResponse explainEventSearch(
            @RequestParam String city,
            @RequestParam String category,
            @RequestParam("from") OffsetDateTime from
    ) {
        return sqlPerformanceService.explainEventSearch(city, category, from);
    }
}