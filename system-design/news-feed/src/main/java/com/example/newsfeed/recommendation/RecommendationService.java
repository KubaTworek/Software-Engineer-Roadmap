package com.example.newsfeed.recommendation;

import com.example.newsfeed.embedding.*;
import com.example.newsfeed.post.Post;
import com.example.newsfeed.post.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serwis rekomendacji odpowiedzialny za znalezienie postów,
 * które mogą być interesujące dla użytkownika poza klasycznym follow feedem.
 *
 * W tej implementacji rekomendacje bazują na embeddingach:
 * - użytkownik ma swój embedding preferencji,
 * - posty mają embeddingi treści,
 * - system liczy podobieństwo cosine similarity,
 * - najwyżej podobne posty trafiają jako kandydaci do feedu.
 *
 * Ten serwis nie układa finalnego feedu.
 * On tylko dostarcza kandydatów typu RECOMMENDED.
 *
 * Finalne mieszanie, ranking i diversity robi FeedService + LearningToRankService.
 */
@Service
public class RecommendationService {

    /**
     * Serwis embeddingów.
     *
     * Odpowiada za:
     * - pobranie embeddingu użytkownika,
     * - pobranie embeddingów postów,
     * - liczenie cosine similarity.
     *
     * Embedding reprezentuje użytkownika albo post jako wektor liczb.
     */
    private final EmbeddingService embeddingService;

    /**
     * Repozytorium postów.
     *
     * RecommendationService najpierw wybiera ID postów na podstawie embeddingów,
     * a potem dociąga pełne encje Post z bazy.
     */
    private final PostRepository postRepository;

    /**
     * Maksymalna liczba kandydatów rekomendacyjnych.
     *
     * Konfigurowane w application.yml:
     *
     * newsfeed:
     *   recommendation:
     *     max-candidates: 150
     *
     * Nie chcemy przekazywać do rankingu tysięcy kandydatów,
     * bo scoring feedu musi być szybki.
     */
    private final int maxCandidates;

    /**
     * Wstrzyknięcie zależności i konfiguracji.
     *
     * maxCandidates ogranicza rozmiar listy rekomendacji,
     * zanim trafią one do dalszego rankingu.
     */
    public RecommendationService(
            EmbeddingService embeddingService,
            PostRepository postRepository,
            @Value("${newsfeed.recommendation.max-candidates:150}") int maxCandidates
    ) {
        this.embeddingService = embeddingService;
        this.postRepository = postRepository;
        this.maxCandidates = maxCandidates;
    }

    /**
     * Zwraca listę rekomendowanych postów dla użytkownika.
     *
     * Flow:
     * 1. pobierz embedding użytkownika,
     * 2. jeśli użytkownik nie ma embeddingu — nie generuj rekomendacji,
     * 3. pobierz najnowsze embeddingi postów,
     * 4. policz podobieństwo użytkownik-post,
     * 5. posortuj posty od najbardziej podobnych,
     * 6. ogranicz wynik do maxCandidates,
     * 7. dociągnij pełne encje Post z bazy.
     *
     * Metoda jest readOnly, bo nie zapisuje danych.
     */
    @Transactional(readOnly = true)
    public List<Post> recommendForUser(UUID userId) {
        /*
         * Pobieramy embedding użytkownika.
         *
         * Embedding użytkownika reprezentuje jego preferencje,
         * np. tematy, autorów albo typy treści, z którymi wcześniej wchodził w interakcje.
         */
        Optional<double[]> userEmbedding = embeddingService.getEmbedding("user", userId);

        /*
         * Brak embeddingu użytkownika oznacza cold start.
         *
         * W tej wersji nie zwracamy rekomendacji.
         *
         * Produkcyjnie można tu zastosować fallback:
         * - trending posts,
         * - popularne tematy,
         * - onboarding interests,
         * - posty z regionu/języka użytkownika.
         */
        if (userEmbedding.isEmpty()) {
            return List.of();
        }

        /*
         * Pobieramy embeddingi ostatnich postów i sortujemy je po podobieństwie
         * do embeddingu użytkownika.
         *
         * cosine similarity:
         * - im bliżej 1.0, tym bardziej podobne wektory,
         * - im bliżej 0.0, tym mniejsze podobieństwo.
         */
        List<UUID> ids = embeddingService.recentPostEmbeddings().stream()
                .sorted((a, b) -> {
                    /*
                     * Score dla posta A.
                     *
                     * vector(a) dekoduje zapisany embedding posta
                     * z formatu tekstowego do double[].
                     */
                    double sa = embeddingService.cosine(
                            userEmbedding.get(),
                            vector(a)
                    );

                    /*
                     * Score dla posta B.
                     */
                    double sb = embeddingService.cosine(
                            userEmbedding.get(),
                            vector(b)
                    );

                    /*
                     * Sortowanie malejąco:
                     * najpierw posty najbardziej podobne do użytkownika.
                     */
                    return Double.compare(sb, sa);
                })

                /*
                 * Ograniczamy liczbę kandydatów.
                 *
                 * To chroni FeedService i LearningToRankService przed
                 * zbyt dużą liczbą postów do dalszego scoringu.
                 */
                .limit(maxCandidates)

                /*
                 * Na tym etapie potrzebujemy tylko ID postów.
                 *
                 * Pełne dane posta zostaną dociągnięte z PostRepository.
                 */
                .map(Embedding::getEntityId)
                .toList();

        /*
         * Jeśli nie znaleźliśmy żadnych kandydatów, zwracamy pustą listę.
         */
        if (ids.isEmpty()) {
            return List.of();
        }

        /*
         * Hydratacja postów.
         *
         * Embedding storage zwraca tylko identyfikatory.
         * Feed potrzebuje pełnych encji Post, więc pobieramy je z bazy.
         *
         * findFeedPostsByIds powinno filtrować:
         * - usunięte posty,
         * - ewentualnie posty ukryte przez moderację,
         * - oraz dociągać autora przez JOIN FETCH.
         */
        return postRepository.findFeedPostsByIds(ids);
    }

    /**
     * Dekoduje embedding posta z formatu tekstowego do tablicy double.
     *
     * W tej implementacji embedding jest zapisany jako string:
     *
     * "0.12,0.44,0.02,0.91"
     *
     * Produkcyjnie embeddingi często trzyma się w:
     * - vector database,
     * - OpenSearch k-NN,
     * - pgvector,
     * - Feature Store,
     * - dedykowanym embedding service.
     */
    private double[] vector(Embedding e) {
        /*
         * Rozbijamy tekstowy zapis wektora po przecinkach.
         */
        String[] parts = e.getVector().split(",");

        /*
         * Tworzymy tablicę double o tej samej długości.
         */
        double[] v = new double[parts.length];

        /*
         * Parsujemy każdą wartość tekstową na double.
         */
        for (int i = 0; i < parts.length; i++) {
            v[i] = Double.parseDouble(parts[i]);
        }

        return v;
    }
}