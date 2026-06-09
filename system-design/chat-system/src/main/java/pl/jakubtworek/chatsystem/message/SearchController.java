package pl.jakubtworek.chatsystem.message;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

import java.util.List;

/**
 * REST controller odpowiedzialny za wyszukiwanie wiadomości.
 *
 * Ten endpoint działa globalnie względem wiadomości użytkownika,
 * a nie względem jednej konkretnej konwersacji.
 *
 * Kluczowe założenie bezpieczeństwa:
 * użytkownik może wyszukiwać tylko wiadomości z konwersacji,
 * do których ma dostęp jako członek.
 *
 * Controller nie powinien samodzielnie filtrować wyników ani sprawdzać uprawnień.
 * To musi robić MessageSearchService, bo tam znajduje się logika biznesowa
 * oraz dostęp do repozytoriów wiadomości, konwersacji i członkostwa.
 */
@RestController
@RequestMapping("/api/messages")
public class SearchController {

    private final MessageSearchService messageSearchService;

    /**
     * Wstrzyknięcie serwisu odpowiedzialnego za wyszukiwanie wiadomości.
     *
     * Dzięki temu controller pozostaje cienką warstwą HTTP:
     * przyjmuje request, wyciąga użytkownika z kontekstu bezpieczeństwa
     * i przekazuje zapytanie do serwisu.
     */
    public SearchController(MessageSearchService messageSearchService) {
        this.messageSearchService = messageSearchService;
    }

    /**
     * Wyszukuje wiadomości dostępne dla aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * GET /api/messages/search?q=tekst&limit=50
     *
     * Parametry:
     * - principal.id() — identyfikator użytkownika wykonującego wyszukiwanie,
     * - q — szukana fraza,
     * - limit — maksymalna liczba wyników, domyślnie 50.
     *
     * Najważniejsze:
     * - wyszukiwanie musi być ograniczone do konwersacji użytkownika,
     * - serwis powinien odrzucić puste lub zbyt krótkie zapytania,
     * - serwis powinien ograniczyć maksymalny limit, np. do 100,
     * - wyniki powinny być posortowane sensownie, najczęściej od najnowszych,
     * - w przyszłości implementację można podmienić z wyszukiwania SQL
     *   na OpenSearch/Elasticsearch bez zmiany kontraktu tego endpointu.
     *
     * Zwracany MessageResponse reprezentuje wiadomości pasujące do frazy,
     * ale nadal tylko te, które użytkownik ma prawo zobaczyć.
     */
    @GetMapping("/search")
    public List<MessageResponse> searchMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String q,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return messageSearchService.search(principal.id(), q, limit);
    }
}