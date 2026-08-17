package com.example.autocomplete.api;

import com.example.autocomplete.index.IndexStats;
import com.example.autocomplete.model.RankedSuggestion;
import com.example.autocomplete.rollout.IndexRegistry;
import com.example.autocomplete.service.AutocompleteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * REST Controller wystawiający publiczne API dla Search Autocomplete.
 *
 * Ta klasa nie zawiera logiki wyszukiwania, rankingu ani bezpieczeństwa.
 * Jej główna rola to:
 * - przyjęcie parametrów requestu,
 * - przekazanie ich do AutocompleteService,
 * - dodanie metadanych technicznych do odpowiedzi,
 * - wystawienie endpointów administracyjnych do obsługi wersji indeksu.
 */
@RestController
@RequestMapping("/api/v1")
public class AutocompleteController {

    /**
     * Główna warstwa aplikacyjna autocomplete.
     *
     * Service orkiestruje właściwą logikę:
     * - normalizację query,
     * - abuse detection,
     * - wybór wariantu A/B,
     * - cache lookup,
     * - pobranie kandydatów z indeksu,
     * - realtime delta index,
     * - policy filtering,
     * - ranking sugestii.
     */
    private final AutocompleteService service;

    /**
     * Rejestr wersji indeksów.
     *
     * Umożliwia:
     * - sprawdzenie dostępnych wersji indeksu,
     * - aktywację nowej wersji,
     * - rollback do poprzedniej wersji.
     *
     * Dzięki temu można wdrażać nowe indeksy bez restartu aplikacji.
     */
    private final IndexRegistry registry;

    public AutocompleteController(AutocompleteService service, IndexRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    /**
     * Główny endpoint autocomplete.
     *
     * Przykład:
     * GET /api/v1/autocomplete?q=iph&userId=u-apple&sessionId=s1&locale=en-US&country=US
     *
     * Parametry kontekstowe są ważne, bo wpływają na ranking:
     * - userId: personalizacja,
     * - sessionId: kontekst aktualnej sesji,
     * - locale: język,
     * - country: lokalne trendy,
     * - category: kontekst kategorii,
     * - IP: abuse detection / rate limiting.
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<AutocompleteResponse> autocomplete(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "locale", required = false) String locale,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "category", required = false) String category,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        /*
         * Limit z requestu nie jest używany bezpośrednio.
         *
         * Service pilnuje bezpiecznych wartości:
         * - domyślnego limitu,
         * - minimalnej wartości,
         * - maksymalnego limitu.
         *
         * Chroni to backend przed zbyt dużymi odpowiedziami.
         */
        int effectiveLimit = service.sanitizeLimit(limit);

        /*
         * IP klienta jest przekazywane do service'u.
         *
         * W tej wersji służy głównie do abuse detection.
         * W produkcji trzeba uważać na proxy/load balancer i zwykle używać
         * np. X-Forwarded-For albo Forwarded Header Filter.
         */
        String ip = request.getRemoteAddr();

        /*
         * Główne wywołanie aplikacyjne.
         *
         * Controller tylko przekazuje dane wejściowe.
         * Cała logika autocomplete znajduje się niżej, w AutocompleteService.
         */
        var result = service.autocomplete(
                query,
                effectiveLimit,
                userId,
                sessionId,
                locale,
                country,
                category,
                ip
        );

        /*
         * Nagłówki diagnostyczne.
         *
         * Są przydatne przy debugowaniu latency, cache i eksperymentów:
         * - czy odpowiedź przyszła z cache,
         * - z której wersji indeksu korzystaliśmy,
         * - jaki wariant A/B został użyty.
         */
        response.addHeader("X-Autocomplete-Cache", result.cacheStatus());
        response.addHeader("X-Autocomplete-Index-Version", result.indexVersion());
        response.addHeader("X-Autocomplete-Experiment", result.experimentVariant().name());

        /*
         * Mapa domenowego modelu rankingowego na response DTO.
         *
         * Nie zwracamy całego obiektu domenowego Suggestion,
         * tylko jawnie wybrane pola potrzebne klientowi.
         */
        List<SuggestionResponse> suggestions = result.suggestions()
                .stream()
                .map(this::toResponse)
                .toList();

        /*
         * cachePrivate oznacza, że odpowiedź może być cache'owana prywatnie
         * przez klienta/przeglądarkę, ale nie powinna być współdzielona publicznie.
         *
         * To ważne, bo odpowiedź może zależeć od userId, sessionId i personalizacji.
         */
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePrivate())
                .body(new AutocompleteResponse(
                        query,
                        effectiveLimit,
                        suggestions.size(),
                        result.cacheStatus(),
                        result.indexVersion(),
                        result.experimentVariant().name(),
                        result.latencyMs(),
                        suggestions
                ));
    }

    /**
     * Endpoint diagnostyczny zwracający statystyki aktywnego indeksu.
     *
     * Przydatny do sprawdzenia:
     * - która wersja indeksu jest aktywna,
     * - ile sugestii zawiera indeks,
     * - ile wariantów zostało zaindeksowanych,
     * - jaki jest rozmiar Trie,
     * - jaki jest max popularity score.
     */
    @GetMapping("/autocomplete/index/stats")
    public ResponseEntity<IndexStats> indexStats() {
        return ResponseEntity.ok(service.indexStats());
    }

    /**
     * Zwraca listę wszystkich wersji indeksu znanych aplikacji.
     *
     * To wspiera rollout:
     * - można mieć starą wersję produkcyjną,
     * - nową wersję canary,
     * - wersję przygotowaną do rollbacku.
     */
    @GetMapping("/autocomplete/index/versions")
    public ResponseEntity<List<String>> versions() {
        return ResponseEntity.ok(registry.versions());
    }

    /**
     * Aktywuje wskazaną wersję indeksu.
     *
     * Przykład:
     * POST /api/v1/autocomplete/index/activate/index-v2-canary
     *
     * W produkcji taki endpoint powinien być zabezpieczony:
     * - autoryzacją,
     * - RBAC,
     * - audytem,
     * - najlepiej niedostępny publicznie.
     */
    @PostMapping("/autocomplete/index/activate/{version}")
    public ResponseEntity<String> activate(@PathVariable String version) {
        registry.activate(version);
        return ResponseEntity.ok("Activated " + version);
    }

    /**
     * Przywraca poprzednią aktywną wersję indeksu.
     *
     * To jest mechanizm awaryjny, gdy nowy indeks:
     * - zwraca słabe sugestie,
     * - ma błędne dane,
     * - powoduje regresję jakości,
     * - zwiększa latency.
     */
    @PostMapping("/autocomplete/index/rollback")
    public ResponseEntity<String> rollback() {
        registry.rollback();
        return ResponseEntity.ok("Rolled back to " + registry.activeVersion());
    }

    /**
     * Mapuje wewnętrzny obiekt RankedSuggestion na DTO zwracane do klienta.
     *
     * Zwracamy nie tylko tekst sugestii, ale też scoring signals,
     * ponieważ na tym etapie projektu chcemy widzieć, dlaczego sugestia
     * znalazła się wysoko w rankingu.
     *
     * W produkcyjnym API część tych pól można ukryć przed klientem
     * i zostawić je tylko w logach/debug endpointach.
     */
    private SuggestionResponse toResponse(RankedSuggestion r) {
        return new SuggestionResponse(
                r.suggestion().id(),
                r.suggestion().displayText(),
                r.suggestion().type(),
                r.suggestion().popularity(),
                r.score(),
                r.personalizationScore(),
                r.localeScore(),
                r.trendingScore(),
                r.sessionScore(),
                r.experimentBoost(),
                r.matchSource()
        );
    }
}