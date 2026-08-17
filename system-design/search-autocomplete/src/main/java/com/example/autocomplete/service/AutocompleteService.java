package com.example.autocomplete.service;

import com.example.autocomplete.abtest.*;
import com.example.autocomplete.abuse.AbuseDetectionService;
import com.example.autocomplete.cache.AutocompleteCache;
import com.example.autocomplete.index.*;
import com.example.autocomplete.language.LanguageDetector;
import com.example.autocomplete.model.*;
import com.example.autocomplete.personalization.SearchHistoryStore;
import com.example.autocomplete.policy.SafetyPolicyFilter;
import com.example.autocomplete.ranking.SuggestionRanker;
import com.example.autocomplete.rollout.IndexRegistry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Główna warstwa aplikacyjna Search Autocomplete.
 *
 * Ta klasa jest orkiestratorem całego requestu:
 * - waliduje limit,
 * - wykrywa język,
 * - normalizuje query,
 * - sprawdza abuse/rate limit,
 * - przypisuje wariant A/B,
 * - wybiera aktywną wersję indeksu,
 * - sprawdza cache,
 * - pobiera kandydatów z indeksu głównego i delta indexu,
 * - filtruje sugestie przez safety policy,
 * - rankinguje wyniki,
 * - zapisuje wynik do cache,
 * - aktualizuje historię sesji,
 * - zapisuje metryki latency i blokad.
 *
 * Controller powinien być cienki, a większość logiki requestu
 * znajduje się właśnie tutaj.
 */
@Service
public class AutocompleteService {

    /**
     * Domyślna liczba sugestii zwracana klientowi,
     * jeśli request nie poda parametru limit.
     */
    private static final int DEFAULT_LIMIT = 10;

    /**
     * Twardy limit bezpieczeństwa.
     *
     * Chroni system przed requestami typu:
     * /autocomplete?q=iph&limit=100000
     */
    private static final int MAX_LIMIT = 20;

    /**
     * Pobieramy więcej kandydatów niż finalnie zwracamy.
     *
     * Przykład:
     * limit = 10
     * candidate limit = 100
     *
     * Dzięki temu ranker ma z czego wybierać.
     * Gdybyśmy pobrali tylko 10 kandydatów z indeksu, ranking byłby zbyt ograniczony.
     */
    private static final int CANDIDATE_MULTIPLIER = 10;

    /**
     * Rejestr aktywnych wersji indeksu.
     *
     * Service nie wie, która wersja indeksu jest produkcyjna.
     * Pyta registry.active() i dostaje aktualnie aktywny indeks.
     *
     * To umożliwia rollout i rollback indeksów bez restartu aplikacji.
     */
    private final IndexRegistry registry;

    /**
     * Realtime delta index.
     *
     * Przechowuje świeże/trending sugestie, które nie muszą jeszcze znajdować się
     * w głównym batchowym indeksie.
     *
     * Dzięki temu system może reagować szybciej niż pełny rebuild indeksu.
     */
    private final RealtimeDeltaIndex deltaIndex;

    /**
     * Moduł rankingu.
     *
     * Sortuje kandydatów według wielu sygnałów:
     * - popularność,
     * - CTR,
     * - konwersje,
     * - jakość,
     * - personalizacja,
     * - locale/country,
     * - trendy,
     * - wariant eksperymentu A/B.
     */
    private final SuggestionRanker ranker;

    /**
     * Normalizuje query.
     *
     * Przykład:
     * "  iPhone-15 Pro!! " -> "iphone 15 pro"
     *
     * Normalizacja jest ważna dla cache key, wyszukiwania i rankingu.
     */
    private final TextNormalizer normalizer;

    /**
     * Wykrywa lub ustala locale requestu.
     *
     * Jeśli klient poda locale, używamy go.
     * Jeśli nie, można próbować wykryć język z query.
     */
    private final LanguageDetector languageDetector;

    /**
     * Filtr bezpieczeństwa dla sugestii.
     *
     * Usuwa sugestie:
     * - ręcznie zablokowane,
     * - spamowe,
     * - niskiej jakości,
     * - zawierające zablokowane terminy.
     */
    private final SafetyPolicyFilter safetyFilter;

    /**
     * Moduł ochrony przed abuse.
     *
     * Może blokować requesty np. po IP, gdy klient przekracza limit requestów.
     * To chroni autocomplete przed scrapingiem i nadmiernym ruchem.
     */
    private final AbuseDetectionService abuseDetection;

    /**
     * Stabilne przypisanie użytkownika do wariantu eksperymentu A/B.
     *
     * Wariant wpływa na ranking, np.:
     * - CONTROL,
     * - CTR_HEAVY,
     * - TRENDING_HEAVY.
     */
    private final ExperimentAssignmentService experimentService;

    /**
     * Historia wyszukiwań w aktualnej sesji.
     *
     * Używana do kontekstowego rankingu.
     * Przykład: jeśli w sesji użytkownik szukał "macbook",
     * kolejne sugestie Apple mogą dostać dodatkowy boost.
     */
    private final SearchHistoryStore historyStore;

    /**
     * Lokalny cache autocomplete.
     *
     * Cache key zawiera nie tylko query, ale też:
     * - userId,
     * - sessionId,
     * - locale,
     * - country,
     * - category,
     * - wariant A/B,
     * - wersję indeksu,
     * - limit.
     *
     * To ważne, bo odpowiedź może się różnić dla różnych użytkowników
     * i różnych wersji indeksu.
     */
    private final AutocompleteCache cache;

    /**
     * Metryka latency całego requestu autocomplete.
     *
     * Eksportowana przez Micrometer/Actuator/Prometheus.
     */
    private final Timer timer;

    /**
     * Licznik requestów zablokowanych przez abuse detection lub policy.
     *
     * W tej klasie inkrementowany przy abuse block.
     * Można go rozszerzyć także o blokady safety/policy.
     */
    private final Counter blockedCounter;

    public AutocompleteService(IndexRegistry registry, RealtimeDeltaIndex deltaIndex, SuggestionRanker ranker,
                               TextNormalizer normalizer, LanguageDetector languageDetector, SafetyPolicyFilter safetyFilter,
                               AbuseDetectionService abuseDetection, ExperimentAssignmentService experimentService,
                               SearchHistoryStore historyStore, AutocompleteCache cache, MeterRegistry meterRegistry) {
        this.registry = registry;
        this.deltaIndex = deltaIndex;
        this.ranker = ranker;
        this.normalizer = normalizer;
        this.languageDetector = languageDetector;
        this.safetyFilter = safetyFilter;
        this.abuseDetection = abuseDetection;
        this.experimentService = experimentService;
        this.historyStore = historyStore;
        this.cache = cache;

        /*
         * Rejestrujemy metryki techniczne.
         *
         * autocomplete_platform_latency:
         * - mierzy czas obsługi requestu autocomplete.
         *
         * autocomplete_policy_or_abuse_blocks:
         * - liczy requesty/sytuacje zablokowane ze względów bezpieczeństwa.
         */
        this.timer = meterRegistry.timer("autocomplete_platform_latency");
        this.blockedCounter = meterRegistry.counter("autocomplete_policy_or_abuse_blocks");
    }

    /**
     * Główna metoda obsługująca autocomplete.
     *
     * To jest pełny request flow:
     *
     * 1. Pomiar czasu.
     * 2. Sanityzacja limitu.
     * 3. Detekcja języka.
     * 4. Normalizacja query.
     * 5. Zbudowanie kontekstu requestu.
     * 6. Abuse detection.
     * 7. Assignment do eksperymentu A/B.
     * 8. Pobranie aktywnego indeksu.
     * 9. Cache lookup.
     * 10. Candidate generation.
     * 11. Safety filtering.
     * 12. Ranking.
     * 13. Cache write.
     * 14. Aktualizacja historii sesji.
     * 15. Zapis latency.
     * 16. Zwrócenie odpowiedzi.
     */
    public AutocompleteResult autocomplete(
            String query,
            Integer requestedLimit,
            String userId,
            String sessionId,
            String locale,
            String country,
            String category,
            String clientIp
    ) {
        /*
         * Start pomiaru latency.
         *
         * Używamy nanoTime, bo nadaje się do mierzenia czasu trwania operacji.
         */
        long start = System.nanoTime();

        /*
         * Nie ufamy limitowi z requestu.
         *
         * Limit jest ograniczany do zakresu:
         * - default: 10,
         * - max: 20.
         */
        int limit = sanitizeLimit(requestedLimit);

        /*
         * Locale wpływa na ranking i dopasowanie sugestii.
         *
         * Jeśli klient poda locale, detector może go zaakceptować.
         * Jeśli nie, może użyć fallbacku lub wykrywania po query.
         */
        String detectedLocale = languageDetector.detect(query, locale);

        /*
         * Jedna wspólna reprezentacja query dla:
         * - cache key,
         * - wyszukiwania,
         * - rankingu,
         * - historii sesji.
         */
        String normalized = normalizer.normalize(query);

        /*
         * Kontekst requestu agreguje wszystkie informacje wpływające na wynik.
         *
         * Dzięki temu niższe warstwy nie muszą dostawać wielu osobnych parametrów.
         */
        AutocompleteContext ctx = new AutocompleteContext(
                userId,
                sessionId,
                detectedLocale,
                country,
                category,
                query,
                normalized,
                clientIp
        );

        /*
         * Pierwsza bramka bezpieczeństwa.
         *
         * Jeśli klient wygląda na abuse/bota/scrapera, nie wykonujemy:
         * - cache lookup,
         * - wyszukiwania,
         * - rankingu.
         *
         * Oszczędza to zasoby i chroni system.
         */
        if (!abuseDetection.isAllowed(ctx)) {
            blockedCounter.increment();

            return new AutocompleteResult(
                    List.of(),
                    null,
                    "ABUSE_BLOCKED",
                    registry.activeVersion(),
                    ExperimentVariant.CONTROL,
                    elapsedMs(start)
            );
        }

        /*
         * Stabilny wariant eksperymentu rankingowego.
         *
         * Ten sam użytkownik powinien trafiać do tego samego wariantu,
         * żeby wyniki były spójne i żeby można było mierzyć efekty A/B testu.
         */
        ExperimentVariant variant = experimentService.assign(ctx.safeUserId(), "ranking-v6");

        /*
         * Pobieramy aktywny indeks z registry.
         *
         * To jest kluczowe dla rollout/rollback:
         * service nie ma na sztywno przypiętej wersji indeksu.
         */
        AutocompleteIndex activeIndex = registry.active();

        /*
         * Cache key musi zawierać wszystkie elementy, które mogą zmienić wynik.
         *
         * Gdybyśmy użyli tylko query jako klucza, moglibyśmy zwrócić:
         * - wynik innego użytkownika,
         * - wynik dla innego kraju,
         * - wynik z innego eksperymentu,
         * - wynik ze starej wersji indeksu.
         */
        String cacheKey = cache.key(
                normalized,
                ctx.safeUserId(),
                ctx.safeSessionId(),
                ctx.safeLocale(),
                ctx.safeCountry(),
                category,
                variant.name(),
                activeIndex.version(),
                limit
        );

        /*
         * Fast path.
         *
         * Jeśli odpowiedź jest w cache, zwracamy ją od razu.
         * Pomijamy candidate generation, safety filtering i ranking.
         */
        Optional<List<RankedSuggestion>> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return new AutocompleteResult(
                    cached.get(),
                    null,
                    "L1_HIT",
                    activeIndex.version(),
                    variant,
                    elapsedMs(start)
            );
        }

        /*
         * Candidate generation z głównego indeksu.
         *
         * Pobieramy więcej kandydatów niż finalny limit,
         * żeby ranker mógł wybrać najlepsze sugestie.
         */
        List<Suggestion> candidates = new ArrayList<>(
                activeIndex.candidates(query, limit * CANDIDATE_MULTIPLIER)
        );

        /*
         * Dodajemy świeże sugestie z delta indexu.
         *
         * Delta index reprezentuje dane near-real-time:
         * - trendy,
         * - świeże zapytania,
         * - nowe encje,
         * - hot topics.
         *
         * Dzięki temu nie trzeba czekać na pełny batch rebuild indeksu.
         */
        candidates.addAll(deltaIndex.candidates(query, limit * 2));

        /*
         * Safety filtering przed rankingiem.
         *
         * Usuwamy sugestie, które nie powinny zostać pokazane użytkownikowi.
         * To powinno dziać się przed rankingiem, żeby ranker nie promował
         * treści, które i tak są niedozwolone.
         */
        candidates = candidates.stream()
                .filter(s -> safetyFilter.evaluate(s).allowed())
                .toList();

        /*
         * Ranking finalny.
         *
         * Ranker bierze pod uwagę:
         * - metryki sugestii,
         * - kontekst użytkownika,
         * - locale/country,
         * - historię sesji,
         * - trendy,
         * - wariant A/B.
         */
        List<RankedSuggestion> ranked = ranker.rank(
                candidates,
                ctx,
                activeIndex.stats().maxPopularity(),
                limit,
                variant
        );

        /*
         * Zapisujemy wynik do cache.
         *
         * Kolejne identyczne requesty dla tego samego kontekstu
         * będą obsłużone szybciej.
         */
        cache.put(cacheKey, ranked);

        /*
         * Aktualizujemy historię sesji po wyliczeniu odpowiedzi.
         *
         * Ta historia może wpłynąć na ranking kolejnych requestów
         * w tej samej sesji.
         */
        historyStore.recordSessionQuery(ctx.safeSessionId(), normalized);

        /*
         * Zapis latency do metryk.
         *
         * Ta metryka pozwala monitorować p95/p99 i wykrywać regresje.
         */
        timer.record(Duration.ofMillis(elapsedMs(start)));

        /*
         * Zwracamy wynik wraz z metadanymi technicznymi.
         *
         * Metadata jest ważna do debugowania:
         * - cacheStatus,
         * - indexVersion,
         * - experimentVariant,
         * - latencyMs.
         */
        return new AutocompleteResult(
                ranked,
                null,
                "MISS",
                activeIndex.version(),
                variant,
                elapsedMs(start)
        );
    }

    /**
     * Ogranicza limit wyników do bezpiecznego zakresu.
     *
     * To zabezpiecza backend przed nadmiernie dużymi odpowiedziami
     * i utrzymuje przewidywalne latency.
     */
    public int sanitizeLimit(Integer l) {
        return l == null || l < 1
                ? DEFAULT_LIMIT
                : Math.min(l, MAX_LIMIT);
    }

    /**
     * Zwraca statystyki aktywnego indeksu.
     *
     * Używane przez endpoint diagnostyczny/adminowy.
     */
    public IndexStats indexStats() {
        return registry.active().stats();
    }

    /**
     * Liczy czas od startu requestu w milisekundach.
     */
    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * DTO wyniku zwracanego z warstwy service do controllera.
     *
     * Zawiera nie tylko sugestie, ale też metadane runtime:
     * - correctedQuery: miejsce pod spell correction,
     * - cacheStatus: HIT/MISS/BLOCK,
     * - indexVersion: użyta wersja indeksu,
     * - experimentVariant: wariant A/B,
     * - latencyMs: czas obsługi requestu.
     */
    public record AutocompleteResult(
            List<RankedSuggestion> suggestions,
            String correctedQuery,
            String cacheStatus,
            String indexVersion,
            ExperimentVariant experimentVariant,
            long latencyMs
    ) {
    }
}