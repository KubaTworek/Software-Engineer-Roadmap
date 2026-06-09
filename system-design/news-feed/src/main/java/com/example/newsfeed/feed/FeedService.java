package com.example.newsfeed.feed;

import com.example.newsfeed.celebrity.CelebrityService;
import com.example.newsfeed.experiment.ExperimentService;
import com.example.newsfeed.follow.FollowRepository;
import com.example.newsfeed.post.*;
import com.example.newsfeed.ranking.*;
import com.example.newsfeed.recommendation.RecommendationService;
import com.example.newsfeed.user.User;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serwis odpowiedzialny za zbudowanie feedu użytkownika.
 *
 * To jest jedna z najważniejszych klas w aplikacji News Feed.
 *
 * FeedService składa feed z wielu źródeł:
 * - precomputed feed_inbox,
 * - posty obserwowanych autorów,
 * - posty celebrytów w modelu pull,
 * - rekomendacje ML,
 * - ranking learning-to-rank,
 * - A/B testing wariantu rankingu,
 * - Redis cache,
 * - fallback na global feed przy awarii.
 *
 * Controller tylko odbiera request HTTP.
 * Cała logika budowania feedu znajduje się tutaj.
 */
@Service
public class FeedService {

    /**
     * Domyślna liczba elementów feedu na stronie.
     *
     * Używana, gdy klient nie poda parametru limit.
     */
    private static final int DEFAULT_LIMIT = 20;

    /**
     * Maksymalna liczba elementów feedu na stronie.
     *
     * Chroni system przed zbyt ciężkimi requestami,
     * np. limit=10000.
     */
    private static final int MAX_LIMIT = 50;

    /**
     * Warstwa dostępu do precomputed feed storage.
     *
     * FeedStorage zwraca ID postów z feed_inbox użytkownika.
     *
     * W Stage 5/6 feed storage może być lokalnie PostgreSQL,
     * ale architektonicznie jest gotowy pod Cassandra/DynamoDB.
     */
    private final FeedStorage feedStorage;

    /**
     * Repozytorium postów.
     *
     * Używane do hydratacji feedu:
     * feed storage zwraca same postId,
     * a PostRepository pobiera pełne encje Post.
     */
    private final PostRepository postRepository;

    /**
     * Repozytorium relacji follow.
     *
     * Potrzebne do pobrania listy autorów obserwowanych przez użytkownika.
     * Ta lista jest używana m.in. do celebrity pull model.
     */
    private final FollowRepository followRepository;

    /**
     * Serwis obsługujący celebrity pull model.
     *
     * Dla bardzo popularnych autorów nie robimy pełnego fan-outu,
     * bo jeden post celebryty mógłby wygenerować miliony wpisów w feed_inbox.
     *
     * Zamiast tego ich posty są dociągane podczas odczytu feedu.
     */
    private final CelebrityService celebrityService;

    /**
     * Cache feedu w Redis.
     *
     * Odczyt feedu jest bardzo częstą operacją,
     * więc wynik składania feedu warto cache’ować krótkoterminowo.
     */
    private final FeedCacheService feedCacheService;

    /**
     * Serwis rekomendacji.
     *
     * Dostarcza dodatkowych kandydatów do feedu,
     * zwykle na podstawie embeddingów użytkownika i treści.
     */
    private final RecommendationService recommendationService;

    /**
     * Serwis rankingu learning-to-rank.
     *
     * Nadaje każdemu kandydatowi score.
     *
     * Score decyduje, które posty będą wyżej w feedzie.
     */
    private final LearningToRankService learningToRankService;

    /**
     * Serwis eksperymentów A/B.
     *
     * Użytkownik dostaje stabilny wariant rankingu,
     * np. control, ltr_v1 albo ltr_v2.
     *
     * Dzięki temu można testować różne modele rankingu.
     */
    private final ExperimentService experimentService;

    /**
     * Metryka czasu budowania feedu.
     *
     * Pozwala obserwować latency endpointu feedu w Prometheusie.
     */
    private final Timer feedLatency;

    /**
     * Licznik fallbacków.
     *
     * Zwiększany, gdy personalized feed nie może zostać zbudowany
     * i system przechodzi na feed globalny.
     */
    private final Counter feedFallbacks;

    /**
     * Wstrzyknięcie wszystkich zależności potrzebnych do zbudowania feedu.
     *
     * Liczba zależności pokazuje, że feed nie jest prostym SELECT-em.
     * To pipeline łączący storage, cache, social graph, ML, ranking i observability.
     */
    public FeedService(FeedStorage feedStorage, PostRepository postRepository, FollowRepository followRepository,
                       CelebrityService celebrityService, FeedCacheService feedCacheService,
                       RecommendationService recommendationService, LearningToRankService learningToRankService,
                       ExperimentService experimentService, MeterRegistry meterRegistry) {
        this.feedStorage = feedStorage;
        this.postRepository = postRepository;
        this.followRepository = followRepository;
        this.celebrityService = celebrityService;
        this.feedCacheService = feedCacheService;
        this.recommendationService = recommendationService;
        this.learningToRankService = learningToRankService;
        this.experimentService = experimentService;

        /*
         * Rejestrujemy metrykę latency feedu.
         *
         * Każde wywołanie getPersonalizedFeed mierzy czas wykonania
         * i publikuje go do Micrometera.
         */
        this.feedLatency = Timer.builder("newsfeed.feed.read.latency")
                .register(meterRegistry);

        /*
         * Rejestrujemy licznik fallbacków.
         *
         * Jeśli personalized feed padnie i zwrócimy global feed,
         * ta metryka pozwoli to wykryć.
         */
        this.feedFallbacks = Counter.builder("newsfeed.feed.fallbacks")
                .register(meterRegistry);
    }

    /**
     * Buduje spersonalizowany feed użytkownika.
     *
     * To główny endpoint aplikacji.
     *
     * Flow:
     * 1. normalizacja limitu,
     * 2. przypisanie użytkownika do wariantu eksperymentu rankingowego,
     * 3. próba odczytu feedu z cache,
     * 4. dekodowanie cursora,
     * 5. pobranie kandydatów z feed_inbox,
     * 6. pobranie postów celebrytów przez pull model,
     * 7. pobranie rekomendacji,
     * 8. deduplikacja kandydatów,
     * 9. hydratacja postów po ID,
     * 10. oznaczenie źródła kandydata,
     * 11. scoring learning-to-rank,
     * 12. sortowanie po score,
     * 13. diversity rules,
     * 14. budowa nextCursor,
     * 15. zapis do cache,
     * 16. zwrócenie FeedResponse.
     *
     * CircuitBreaker chroni endpoint przed awarią feedStorage.
     * Jeśli feedStorage przestanie działać, uruchomi się fallback.
     */
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "feedStorage", fallbackMethod = "getPersonalizedFeedFallback")
    public FeedResponse getPersonalizedFeed(User currentUser, Integer requestedLimit, String encodedCursor) {
        /*
         * feedLatency.record mierzy czas wykonania całego pipeline’u feedu.
         *
         * To ważne, bo feed jest jednym z najbardziej krytycznych endpointów
         * pod kątem wydajności.
         */
        return feedLatency.record(() -> {
            /*
             * Normalizujemy limit.
             *
             * Nie ufamy wartości przesłanej przez klienta,
             * bo zbyt duży limit może obciążyć storage, ranking i cache.
             */
            int limit = normalizeLimit(requestedLimit);

            /*
             * A/B testing rankingu.
             *
             * Ten sam użytkownik powinien dostawać stabilny wariant,
             * żeby wyniki eksperymentu były porównywalne.
             */
            String variant = experimentService.assignVariant(
                    currentUser.getId(),
                    "feed-ranking"
            );

            /*
             * Klucz cache musi zawierać:
             * - użytkownika,
             * - wariant eksperymentu,
             * - limit,
             * - cursor.
             *
             * Inaczej moglibyśmy zwrócić feed policzony dla innego wariantu
             * albo innej strony paginacji.
             */
            String cacheKey = "stage6:user=" + currentUser.getId()
                    + ":variant=" + variant
                    + ":limit=" + limit
                    + ":cursor=" + (encodedCursor == null ? "first" : encodedCursor);

            /*
             * Szybka ścieżka: Redis cache.
             *
             * Jeśli feed dla tego użytkownika i tej strony już istnieje,
             * unikamy kosztownego składania kandydatów i rankingu.
             */
            Optional<FeedResponse> cached = feedCacheService.getPersonalizedFeed(
                    currentUser.getId(),
                    cacheKey
            );

            if (cached.isPresent()) {
                return cached.get();
            }

            /*
             * Cursor pagination.
             *
             * Cursor mówi, od którego miejsca kontynuować odczyt feed_inbox.
             * Brak cursora oznacza pierwszą stronę.
             */
            Optional<FeedCursor> cursor = FeedCursor.decode(encodedCursor);

            /*
             * Kandydaci z precomputed feed_inbox.
             *
             * To są głównie posty obserwowanych autorów,
             * wcześniej zapisane przez fan-out worker.
             *
             * Pobieramy więcej niż limit, bo część kandydatów może zostać
             * odfiltrowana przez ranking/diversity.
             */
            List<UUID> inboxPostIds = cursor
                    .map(c -> feedStorage.getPostIds(
                            currentUser.getId(),
                            c.createdAt(),
                            c.id(),
                            limit * 5
                    ))
                    .orElseGet(() -> feedStorage.getPostIds(
                            currentUser.getId(),
                            null,
                            null,
                            limit * 5
                    ));

            /*
             * Pobieramy follow graph użytkownika.
             *
             * Jest potrzebny do celebrity pull model,
             * bo posty celebrytów niekoniecznie są w feed_inbox.
             */
            Set<UUID> followedIds = followRepository.findFolloweeIds(
                    currentUser.getId()
            );

            /*
             * Celebrity pull model.
             *
             * Dla bardzo popularnych autorów nie zapisujemy ich postów
             * do feed_inbox każdego obserwującego.
             *
             * Zamiast tego dociągamy ich najnowsze posty podczas odczytu feedu.
             */
            Set<UUID> celebrityPostIds = celebrityService.getRecentCelebrityPostIds(
                    followedIds,
                    limit
            );

            /*
             * Rekomendacje ML.
             *
             * RecommendationService zwraca posty potencjalnie interesujące
             * dla użytkownika, nawet jeśli nie pochodzą z follow graphu.
             */
            List<Post> recommended = recommendationService.recommendForUser(
                    currentUser.getId()
            );

            /*
             * Deduplikacja kandydatów.
             *
             * Ten sam post może pojawić się z wielu źródeł:
             * - feed_inbox,
             * - celebrity pull,
             * - recommendations.
             *
             * LinkedHashSet usuwa duplikaty i zachowuje kolejność dodawania.
             */
            LinkedHashSet<UUID> candidateIds = new LinkedHashSet<>();
            candidateIds.addAll(inboxPostIds);
            candidateIds.addAll(celebrityPostIds);
            candidateIds.addAll(
                    recommended.stream()
                            .map(Post::getId)
                            .toList()
            );

            /*
             * Hydratacja postów.
             *
             * Kandydaci to początkowo same UUID.
             * Tutaj dociągamy pełne encje Post razem z autorem.
             */
            List<Post> posts = candidateIds.isEmpty()
                    ? List.of()
                    : postRepository.findFeedPostsByIds(candidateIds);

            /*
             * Mapa źródła kandydata.
             *
             * Źródło jest jednym z sygnałów rankingowych:
             * FOLLOWING może mieć inny boost niż RECOMMENDED czy CELEBRITY.
             */
            Map<UUID, String> source = new HashMap<>();

            inboxPostIds.forEach(id -> source.put(id, "FOLLOWING"));
            celebrityPostIds.forEach(id -> source.put(id, "CELEBRITY"));

            /*
             * putIfAbsent jest celowe:
             * jeśli post jest już z FOLLOWING lub CELEBRITY,
             * nie nadpisujemy go źródłem RECOMMENDED.
             */
            recommended.forEach(p -> source.putIfAbsent(
                    p.getId(),
                    "RECOMMENDED"
            ));

            /*
             * Ranking learning-to-rank.
             *
             * Każdy post dostaje score zależny m.in. od:
             * - wariantu eksperymentu,
             * - freshness,
             * - jakości,
             * - źródła,
             * - cech z Feature Store.
             */
            List<RankedCandidate> ranked = posts.stream()
                    .map(p -> learningToRankService.score(
                            currentUser.getId(),
                            p,
                            source.getOrDefault(p.getId(), "UNKNOWN"),
                            variant
                    ))
                    .sorted(Comparator.comparing(RankedCandidate::score).reversed())
                    .toList();

            /*
             * Diversity rules.
             *
             * Nie chcemy feedu zdominowanego przez jednego autora
             * albo same rekomendacje.
             *
             * Dlatego po rankingu nakładamy proste ograniczenia jakościowe.
             */
            List<Post> pageItems = applyDiversity(ranked, limit).stream()
                    .map(RankedCandidate::post)
                    .toList();

            /*
             * Budowa nextCursor.
             *
             * Cursor opiera się na ostatnim poście zwróconym klientowi.
             *
             * Uwaga: tutaj cursor bazuje na createdAt/id posta,
             * a nie na pozycji po rankingu. Dla stabilnego rankingu produkcyjnego
             * lepszy byłby feed session cursor przechowujący listę ranked IDs.
             */
            String nextCursor = null;
            if (!pageItems.isEmpty() && ranked.size() > limit) {
                Post last = pageItems.get(pageItems.size() - 1);
                nextCursor = new FeedCursor(
                        last.getCreatedAt(),
                        last.getId()
                ).encode();
            }

            /*
             * DTO odpowiedzi.
             *
             * API zwraca PostResponse, a nie encje JPA.
             */
            FeedResponse response = new FeedResponse(
                    pageItems.stream()
                            .map(PostResponse::from)
                            .toList(),
                    nextCursor
            );

            /*
             * Zapis do cache.
             *
             * Kolejne żądanie tej samej strony feedu będzie mogło ominąć
             * kosztowny pipeline budowania i rankingu.
             */
            feedCacheService.putPersonalizedFeed(
                    currentUser.getId(),
                    cacheKey,
                    response
            );

            return response;
        });
    }

    /**
     * Fallback dla spersonalizowanego feedu.
     *
     * Uruchamia się, gdy CircuitBreaker uzna, że feedStorage jest niedostępny
     * albo metoda getPersonalizedFeed rzuci wyjątek objęty circuit breakerem.
     *
     * Zamiast zwracać błąd 500, system degraduje funkcjonalność:
     * użytkownik dostaje prosty globalny feed.
     */
    public FeedResponse getPersonalizedFeedFallback(
            User currentUser,
            Integer requestedLimit,
            String encodedCursor,
            Throwable throwable
    ) {
        /*
         * Zwiększamy metrykę fallbacków.
         *
         * Jeśli ta wartość rośnie, oznacza to problem z personalizacją
         * albo feed storage.
         */
        feedFallbacks.increment();

        int limit = normalizeLimit(requestedLimit);

        /*
         * Awaryjny feed.
         *
         * Pobieramy najnowsze posty globalne, bez personalizacji,
         * rekomendacji i rankingu ML.
         *
         * To gorsze doświadczenie, ale aplikacja nadal działa.
         */
        List<Post> fallback = postRepository.findFirstGlobalPage(
                org.springframework.data.domain.PageRequest.of(0, limit)
        );

        return new FeedResponse(
                fallback.stream()
                        .map(PostResponse::from)
                        .toList(),
                null
        );
    }

    /**
     * Pobiera globalny feed.
     *
     * Global feed jest prostym feedem bez personalizacji.
     *
     * Typowe użycia:
     * - fallback,
     * - feed dla nowych użytkowników,
     * - debugowanie,
     * - publiczny widok najnowszych postów.
     */
    @Transactional(readOnly = true)
    public FeedResponse getGlobalFeed(Integer requestedLimit, String encodedCursor) {
        /*
         * Normalizujemy limit tak samo jak w feedzie personalizowanym.
         */
        int limit = normalizeLimit(requestedLimit);

        /*
         * Aktualna implementacja pobiera pierwszą stronę globalnego feedu.
         *
         * Parametr encodedCursor jest w sygnaturze,
         * ale w tej wersji nie jest jeszcze używany.
         *
         * W pełnej wersji trzeba użyć FeedCursor.decode(encodedCursor)
         * oraz findNextGlobalPage(...).
         */
        List<Post> posts = postRepository.findFirstGlobalPage(
                org.springframework.data.domain.PageRequest.of(0, limit)
        );

        return new FeedResponse(
                posts.stream()
                        .map(PostResponse::from)
                        .toList(),
                null
        );
    }

    /**
     * Nakłada proste reguły diversity po rankingu.
     *
     * Ranking może ustawić bardzo wysoko wiele postów jednego autora
     * albo bardzo dużo rekomendacji.
     *
     * Diversity rules poprawiają jakość feedu:
     * - maksymalnie 2 posty jednego autora na stronę,
     * - ograniczona liczba rekomendacji.
     */
    private List<RankedCandidate> applyDiversity(List<RankedCandidate> ranked, int limit) {
        List<RankedCandidate> result = new ArrayList<>();

        /*
         * Liczymy, ile postów danego autora już weszło na stronę.
         *
         * Dzięki temu jeden autor nie zdominuje całego feedu.
         */
        Map<UUID, Integer> authorCounts = new HashMap<>();

        /*
         * Liczba rekomendacji dodanych na stronę.
         *
         * Feed nie powinien składać się wyłącznie z rekomendacji,
         * jeśli użytkownik obserwuje realnych autorów.
         */
        int recommended = 0;

        for (RankedCandidate c : ranked) {
            UUID authorId = c.post().getAuthor().getId();

            /*
             * Maksymalnie 2 posty jednego autora.
             *
             * To prosta ochrona przed monotonnym feedem.
             */
            if (authorCounts.getOrDefault(authorId, 0) >= 2) {
                continue;
            }

            /*
             * Ograniczamy udział rekomendacji.
             *
             * Rekomendacje są ważne, ale nie powinny całkowicie wypierać
             * treści z follow graphu.
             */
            if ("RECOMMENDED".equals(c.source()) && recommended >= Math.max(3, limit / 3)) {
                continue;
            }

            result.add(c);

            /*
             * Aktualizujemy liczniki diversity.
             */
            authorCounts.merge(authorId, 1, Integer::sum);

            if ("RECOMMENDED".equals(c.source())) {
                recommended++;
            }

            /*
             * Kończymy, gdy uzbieraliśmy pełną stronę feedu.
             */
            if (result.size() >= limit) {
                break;
            }
        }

        return result;
    }

    /**
     * Normalizuje limit podany przez klienta.
     *
     * Zasady:
     * - brak limitu -> DEFAULT_LIMIT,
     * - limit < 1 -> DEFAULT_LIMIT,
     * - limit > MAX_LIMIT -> MAX_LIMIT.
     *
     * To chroni backend przed zbyt ciężkimi requestami.
     */
    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit < 1) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requestedLimit, MAX_LIMIT);
    }
}