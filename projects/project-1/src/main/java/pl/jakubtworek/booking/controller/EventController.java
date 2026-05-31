package pl.jakubtworek.booking.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.dto.EventSearchResponse;
import pl.jakubtworek.booking.service.EventService;
import pl.jakubtworek.booking.service.SqlPerformanceService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Kontroler REST odpowiedzialny za operacje HTTP związane z eventami.
 *
 * W klasycznym monolicie warstwowym kontroler powinien być cienki:
 * - przyjmuje request HTTP,
 * - waliduje wejście,
 * - deleguje logikę do serwisu,
 * - zwraca DTO jako odpowiedź.
 *
 * Kontroler nie powinien zawierać logiki biznesowej ani bezpośrednio pracować
 * z encjami/repozytoriami.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    /**
     * Główny serwis obsługujący podstawowe operacje na eventach,
     * np. tworzenie eventu i pobieranie eventu po ID.
     */
    private final EventService eventService;

    /**
     * Serwis dodany w etapie SQL/performance.
     *
     * Oddzielenie go od EventService ma tutaj sens edukacyjny:
     * metody wyszukiwania mogą być optymalizowane pod konkretne zapytania,
     * indeksy, projection DTO i EXPLAIN ANALYZE.
     *
     * W większym projekcie można by dyskutować, czy ten serwis nie powinien
     * nazywać się np. EventQueryService, bo nazwa SqlPerformanceService jest
     * mocno techniczna i edukacyjna.
     */
    private final SqlPerformanceService sqlPerformanceService;

    /**
     * Constructor injection.
     *
     * To preferowany sposób wstrzykiwania zależności w Springu:
     * - zależności są jawne,
     * - pola mogą być final,
     * - klasa jest łatwiejsza do testowania,
     * - obiekt nie może powstać bez wymaganych zależności.
     */
    public EventController(EventService eventService,
                           SqlPerformanceService sqlPerformanceService) {
        this.eventService = eventService;
        this.sqlPerformanceService = sqlPerformanceService;
    }

    /**
     * Tworzy nowy event.
     *
     * @Valid uruchamia walidację Bean Validation na DTO wejściowym.
     * Jeśli request nie spełnia reguł walidacji, Spring zwróci błąd 400,
     * zwykle obsługiwany przez globalny exception handler.
     *
     * @ResponseStatus(HttpStatus.CREATED) oznacza, że poprawne utworzenie
     * zasobu zwróci HTTP 201 zamiast domyślnego 200.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventCreateRequest request) {
        return eventService.create(request);
    }

    /**
     * Wyszukuje eventy po access patternie:
     * city + from + category.
     *
     * Ten endpoint jest częścią etapu SQL/performance, ponieważ dobrze nadaje się
     * do analizy indeksu złożonego:
     *
     * CREATE INDEX idx_event_city_category_start_time
     * ON events(city, category, start_time);
     *
     * Parametr "from" mapuje się na OffsetDateTime, więc klient powinien wysłać
     * datę w formacie ISO-8601, np.:
     *
     * 2026-06-01T00:00:00Z
     *
     * Zwracane jest DTO projekcyjne, a nie pełna encja, żeby ograniczyć ilość
     * danych pobieranych z bazy i uniknąć przypadkowego lazy loadingu.
     */
    @GetMapping
    public List<EventSearchResponse> search(@RequestParam String city,
                                            @RequestParam("from") OffsetDateTime from,
                                            @RequestParam String category) {
        return sqlPerformanceService.searchEvents(city, from, category);
    }

    /**
     * Pobiera pojedynczy event po identyfikatorze.
     *
     * UUID w ścieżce zostanie automatycznie sparsowany przez Springa.
     * Jeśli format UUID jest błędny, request zakończy się błędem 400.
     *
     * Jeśli event nie istnieje, EventService powinien rzucić wyjątek domenowy
     * lub aplikacyjny, który globalny exception handler zmapuje np. na HTTP 404.
     */
    @GetMapping("/{eventId}")
    public EventResponse get(@PathVariable UUID eventId) {
        return eventService.get(eventId);
    }
}