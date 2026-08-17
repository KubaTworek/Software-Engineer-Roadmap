package com.example.newsfeed.feed;

import com.example.newsfeed.auth.CurrentUser;
import com.example.newsfeed.user.User;
import org.springframework.web.bind.annotation.*;

/**
 * Kontroler HTTP odpowiedzialny za odczyt feedu.
 *
 * To jest warstwa API dla dwóch typów feedu:
 * - spersonalizowanego feedu użytkownika,
 * - globalnego feedu wszystkich postów.
 *
 * Kontroler nie buduje feedu samodzielnie.
 * Nie wykonuje rankingu, rekomendacji, mieszania źródeł,
 * cache lookup ani cursor pagination.
 *
 * Cała logika znajduje się w FeedService.
 */
@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    /**
     * Serwis biznesowy odpowiedzialny za składanie feedu.
     *
     * FeedService może wykorzystywać:
     * - feed_inbox,
     * - Redis cache,
     * - recommendation service,
     * - celebrity pull model,
     * - learning-to-rank,
     * - A/B testing,
     * - cursor pagination,
     * - graceful degradation.
     */
    private final FeedService feedService;

    /**
     * Wstrzyknięcie FeedService przez konstruktor.
     *
     * Kontroler pozostaje cienki — tylko przyjmuje request
     * i deleguje pracę do serwisu.
     */
    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    /**
     * Pobiera spersonalizowany feed aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * GET /api/v1/feed
     *
     * @CurrentUser dostarcza użytkownika z tokena Authorization.
     * Dzięki temu backend wie, dla kogo ma zbudować feed.
     *
     * Parametry:
     * - limit: liczba elementów na stronie,
     * - cursor: wskaźnik kolejnej strony.
     *
     * Ten endpoint jest głównym feedem aplikacji.
     * Powinien zwracać treści dopasowane do użytkownika, np.:
     * - posty obserwowanych autorów,
     * - posty celebrytów w modelu pull,
     * - rekomendacje,
     * - trending,
     * - ewentualnie sponsored content.
     */
    @GetMapping
    public FeedResponse getPersonalizedFeed(
            @CurrentUser User currentUser,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        /*
         * Kontroler nie przyjmuje userId z requestu.
         *
         * Feed zawsze budowany jest dla aktualnie zalogowanego użytkownika.
         * To chroni przed pobraniem spersonalizowanego feedu innej osoby.
         */
        return feedService.getPersonalizedFeed(currentUser, limit, cursor);
    }

    /**
     * Pobiera globalny feed.
     *
     * Endpoint:
     * GET /api/v1/feed/global
     *
     * Globalny feed nie wymaga @CurrentUser, bo nie jest personalizowany.
     *
     * Typowe zastosowania:
     * - fallback, gdy personalized feed nie działa,
     * - feed dla nowych użytkowników bez follow graph,
     * - publiczny widok najnowszych postów,
     * - debugowanie i testowanie systemu.
     *
     * Parametry:
     * - limit: liczba elementów na stronie,
     * - cursor: wskaźnik kolejnej strony.
     */
    @GetMapping("/global")
    public FeedResponse getGlobalFeed(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        /*
         * Globalny feed zwykle jest prostszy:
         * najczęściej sortowanie po createdAt DESC.
         *
         * W produkcji może być też używany jako graceful fallback,
         * jeśli Redis, ranking, recommendation service albo feed storage
         * chwilowo nie działają.
         */
        return feedService.getGlobalFeed(limit, cursor);
    }
}