package pl.jakubtworek.booking.controller.nosql;

import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.nosql.EventSearchDocumentListResponse;
import pl.jakubtworek.booking.dto.nosql.EventSearchDocumentResponse;
import pl.jakubtworek.booking.service.EventSearchReadModelService;

import java.util.UUID;

/**
 * Kontroler REST dla dokumentowego read modelu eventów.
 *
 * Ten kontroler należy do etapu NoSQL/cache.
 *
 * Jego celem jest pokazanie wzorca:
 *
 * - PostgreSQL pozostaje źródłem prawdy,
 * - MongoDB przechowuje denormalizowany dokument do szybkiego odczytu,
 * - read model może być chwilowo nieaktualny,
 * - read model można odbudować na podstawie danych relacyjnych.
 *
 * To nie jest osobny mikroserwis.
 * To nadal zwykły monolit, tylko z dodatkową bazą pomocniczą do odczytu.
 */
@RestController
@RequestMapping("/api/nosql/read-model/events")
public class EventReadModelController {

    /**
     * Serwis odpowiedzialny za operacje na read modelu eventów.
     *
     * Kontroler nie powinien sam znać szczegółów MongoDB ani sposobu budowania
     * dokumentu. Jego zadaniem jest tylko przyjęcie requestu i delegacja do serwisu.
     */
    private final EventSearchReadModelService eventSearchReadModelService;

    /**
     * Constructor injection.
     *
     * Dzięki temu zależność jest jawna, pole może być final, a kontroler jest
     * łatwiejszy do testowania.
     */
    public EventReadModelController(EventSearchReadModelService eventSearchReadModelService) {
        this.eventSearchReadModelService = eventSearchReadModelService;
    }

    /**
     * Odbudowuje dokument read modelu dla jednego eventu.
     *
     * Endpoint:
     *
     * POST /api/nosql/read-model/events/{eventId}/rebuild
     *
     * Ta operacja powinna:
     * - pobrać aktualne dane eventu z PostgreSQL,
     * - zbudować denormalizowany EventSearchDocument,
     * - zapisać go w read modelu, np. w MongoDB albo implementacji in-memory.
     *
     * To jest świadome pokazanie eventual consistency:
     * dane w read modelu mogą być stare, dopóki dokument nie zostanie odbudowany.
     */
    @PostMapping("/{eventId}/rebuild")
    public EventSearchDocumentResponse rebuild(@PathVariable UUID eventId) {
        return eventSearchReadModelService.rebuildOne(eventId);
    }

    /**
     * Pobiera dokument read modelu dla jednego eventu.
     *
     * Endpoint:
     *
     * GET /api/nosql/read-model/events/{eventId}
     *
     * Ten odczyt nie musi iść do relacyjnych tabel event/reservation/capacity.
     * Może korzystać z gotowego dokumentu zoptymalizowanego pod odczyt.
     *
     * Uwaga:
     * jeśli dokument nie został jeszcze odbudowany albo jest stary, wynik może
     * nie odzwierciedlać najnowszego stanu PostgreSQL.
     */
    @GetMapping("/{eventId}")
    public EventSearchDocumentResponse get(@PathVariable UUID eventId) {
        return eventSearchReadModelService.get(eventId);
    }

    /**
     * Wyszukuje eventy w dokumentowym read modelu po access patternie:
     *
     * city + category + limit
     *
     * Endpoint:
     *
     * GET /api/nosql/read-model/events?city=Warsaw&category=music&limit=20
     *
     * Ten endpoint pokazuje modelowanie dokumentu pod zapytania.
     * W MongoDB nie projektujemy danych tak samo jak w modelu relacyjnym.
     * Często przygotowujemy dokument pod konkretne odczyty API.
     */
    @GetMapping
    public EventSearchDocumentListResponse search(@RequestParam String city,
                                                  @RequestParam String category,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return eventSearchReadModelService.search(city, category, limit);
    }

    /**
     * Czyści cały read model.
     *
     * Endpoint:
     *
     * DELETE /api/nosql/read-model/events
     *
     * To endpoint edukacyjny/testowy.
     * Przydaje się do pokazania, że read model jest odtwarzalny z PostgreSQL.
     *
     * Produkcyjnie taka operacja powinna być mocno ograniczona albo dostępna
     * tylko jako zadanie administracyjne, nie publiczne API.
     */
    @DeleteMapping
    public void clear() {
        eventSearchReadModelService.clear();
    }
}