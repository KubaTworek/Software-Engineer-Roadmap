package com.example.newsfeed.trending;

import com.example.newsfeed.feed.FeedCandidate;
import com.example.newsfeed.feed.FeedSource;
import com.example.newsfeed.post.Post;
import com.example.newsfeed.post.PostRepository;
import com.example.newsfeed.stats.PostStats;
import com.example.newsfeed.stats.PostStatsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serwis odpowiedzialny za dostarczanie kandydatów trending do feedu.
 *
 * Trending to źródło postów niezależne od follow graphu użytkownika.
 *
 * Oznacza to, że użytkownik może zobaczyć popularne lub świeże posty,
 * nawet jeśli nie obserwuje ich autora.
 *
 * Ten serwis nie buduje całego feedu.
 * Zwraca tylko kandydatów typu TRENDING.
 *
 * Finalne mieszanie z postami z follow feedu, rekomendacjami i reklamami
 * powinno odbywać się później w FeedService / rankingu.
 */
@Service
public class TrendingService {

    /**
     * Repozytorium postów.
     *
     * Używane do pobrania puli postów, które mogą trafić do sekcji trending.
     *
     * Metoda findTrendingCandidates powinna filtrować m.in.:
     * - posty usunięte,
     * - posty ukryte przez moderację,
     * - ewentualnie zbyt stare posty.
     */
    private final PostRepository postRepository;

    /**
     * Repozytorium statystyk postów.
     *
     * Dostarcza liczniki potrzebne do późniejszego rankingu:
     * - liczba lajków,
     * - liczba komentarzy,
     * - updatedAt projekcji.
     *
     * Same statystyki nie są tutaj bezpośrednio przeliczane.
     * TrendingService tylko je dociąga.
     */
    private final PostStatsRepository postStatsRepository;

    /**
     * Wstrzyknięcie repozytoriów potrzebnych do zbudowania kandydatów trending.
     */
    public TrendingService(
            PostRepository postRepository,
            PostStatsRepository postStatsRepository
    ) {
        this.postRepository = postRepository;
        this.postStatsRepository = postStatsRepository;
    }

    /**
     * Zwraca listę kandydatów trending do feedu.
     *
     * Flow:
     * 1. pobierz kandydatów trending z PostRepository,
     * 2. pobierz statystyki tych postów jednym zapytaniem,
     * 3. zbuduj mapę postId -> PostStats,
     * 4. opakuj każdy post jako FeedCandidate ze źródłem TRENDING.
     *
     * Metoda jest readOnly, bo tylko czyta dane.
     */
    @Transactional(readOnly = true)
    public List<FeedCandidate> candidates(int limit) {
        /*
         * Pobieramy kandydatów trending.
         *
         * PageRequest ogranicza liczbę postów, żeby nie przekazywać
         * do FeedService zbyt dużej puli kandydatów.
         *
         * Sama logika tego, co znaczy "trending", siedzi w repozytorium
         * albo w zapytaniu SQL, np. newest + engagement.
         */
        List<Post> posts = postRepository.findTrendingCandidates(
                PageRequest.of(0, limit)
        );

        /*
         * Pobieramy statystyki wszystkich kandydatów naraz.
         *
         * To jest batch read, zamiast jednego zapytania per post.
         */
        Map<UUID, PostStats> stats = postStatsRepository
                .findAllById(
                        posts.stream()
                                .map(Post::getId)
                                .toList()
                )
                .stream()

                /*
                 * Zamieniamy listę statystyk na mapę:
                 * postId -> PostStats.
                 *
                 * Dzięki temu przy budowaniu FeedCandidate możemy szybko
                 * znaleźć statystyki konkretnego posta.
                 */
                .collect(Collectors.toMap(
                        PostStats::getPostId,
                        item -> item
                ));

        /*
         * Zamieniamy posty na kandydatów feedu.
         *
         * FeedCandidate zawiera:
         * - encję Post,
         * - źródło TRENDING,
         * - statystyki posta,
         * - bazowy boost/score źródła.
         */
        return posts.stream()
                .map(post -> new FeedCandidate(
                        post,
                        FeedSource.TRENDING,
                        stats.get(post.getId()),
                        0.04
                ))
                .toList();
    }
}