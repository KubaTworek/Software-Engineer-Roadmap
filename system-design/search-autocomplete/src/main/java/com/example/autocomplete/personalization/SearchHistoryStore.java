package com.example.autocomplete.personalization;

import org.springframework.stereotype.Component;

import java.util.*;
import com.example.autocomplete.privacy.QueryPrivacyService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

/**
 * In-memory store historii wyszukiwań w ramach sesji.
 *
 * Ten komponent wspiera krótkoterminową personalizację.
 *
 * Przykład:
 * użytkownik w jednej sesji wpisuje:
 * - "macbook"
 * - "apple"
 * - "iphone"
 *
 * Kolejne sugestie związane z Apple mogą dostać wyższy session score
 * w SuggestionRanker.
 *
 * To nie jest trwała historia użytkownika.
 * Dane żyją tylko w pamięci tej instancji aplikacji.
 */
@Component
public class SearchHistoryStore {

    /**
     * Historia zapytań pogrupowana po sessionId.
     *
     * Klucz:
     * - sessionId użytkownika
     *
     * Wartość:
     * - kolejka ostatnich query z tej sesji.
     *
     * Caffeine nakłada granicę liczby sesji i retencję 30 minut. Kluczem nie jest surowy
     * sessionId, lecz jego stabilny HMAC, a zapytanie przechodzi redakcję PII. Sama
     * ArrayDeque nie jest thread-safe, dlatego operacje na kolejce są synchronizowane.
     */
    private final Cache<String, Deque<String>> bySession = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();
    private final QueryPrivacyService privacy;

    public SearchHistoryStore(QueryPrivacyService privacy) {
        this.privacy = privacy;
    }

    /**
     * Zapisuje query do historii danej sesji.
     *
     * Historia jest używana później przez SuggestionRanker,
     * konkretnie przy liczeniu session score.
     *
     * Najnowsze query trafia na początek kolejki.
     */
    public void recordSessionQuery(String sessionId, String query) {

        /*
         * Jeśli brakuje sessionId albo query, nie zapisujemy niczego.
         *
         * Bez sessionId nie da się powiązać zapytania z konkretną sesją.
         * Puste query nie wnosi wartości rankingowej.
         */
        if (sessionId == null || query == null || query.isBlank()) {
            return;
        }

        /*
         * Pobieramy kolejkę dla sesji.
         *
         * Jeśli to pierwsze query w tej sesji, tworzymy nową kolejkę.
         */
        String protectedSessionId = privacy.hashUserId("session:" + sessionId);
        Deque<String> deque = bySession.asMap().computeIfAbsent(protectedSessionId, k -> new ArrayDeque<>());

        /*
         * Synchronizujemy dostęp do kolejki konkretnej sesji.
         *
         * ConcurrentHashMap chroni mapę, ale ArrayDeque sama w sobie
         * nie jest bezpieczna przy równoległych zapisach/odczytach.
         */
        synchronized (deque) {

            /*
             * Usuwamy wcześniejsze wystąpienie tego samego query.
             *
             * Dzięki temu historia nie ma duplikatów, a powtórzone query
             * zostanie przesunięte na początek jako najświeższe.
             */
            String safeQuery = privacy.redactPii(query);
            deque.remove(safeQuery);

            /*
             * Dodajemy query na początek.
             *
             * Początek kolejki oznacza najnowsze zapytania.
             */
            deque.addFirst(safeQuery);

            /*
             * Trzymamy maksymalnie 20 ostatnich zapytań w sesji.
             *
             * To ogranicza zużycie pamięci i utrzymuje historię jako
             * krótkoterminowy kontekst, a nie pełny log aktywności.
             */
            while (deque.size() > 20) {
                deque.removeLast();
            }
        }
    }

    /**
     * Zwraca ostatnie query dla danej sesji.
     *
     * Wynik jest kopią aktualnej kolejki.
     *
     * To ważne, bo nie chcemy zwracać referencji do wewnętrznej struktury,
     * którą ktoś mógłby przypadkowo zmodyfikować poza tym komponentem.
     */
    public List<String> recentSessionQueries(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return List.of();
        Deque<String> deque = bySession.getIfPresent(privacy.hashUserId("session:" + sessionId));

        /*
         * Brak historii dla sesji oznacza pustą listę,
         * a nie null. Dzięki temu ranker ma prostszą logikę.
         */
        if (deque == null) {
            return List.of();
        }

        /*
         * Synchronizujemy odczyt, bo w tym samym czasie inny request
         * może zapisywać nowe query do tej samej sesji.
         */
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }
}
