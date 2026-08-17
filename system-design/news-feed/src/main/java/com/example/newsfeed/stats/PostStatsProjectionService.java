package com.example.newsfeed.stats;

import com.example.newsfeed.comment.CommentRepository;
import com.example.newsfeed.like.PostLikeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za projekcję statystyk posta.
 *
 * Projekcja oznacza tutaj gotowy, zmaterializowany widok liczników,
 * które są często potrzebne przy wyświetlaniu posta.
 *
 * Zamiast przy każdym renderowaniu feedu liczyć:
 * - SELECT COUNT(*) FROM post_likes,
 * - SELECT COUNT(*) FROM comments,
 *
 * system zapisuje gotowe wartości w tabeli post_stats.
 *
 * Dzięki temu feed może szybko pokazać liczby lajków i komentarzy
 * bez kosztownych countów dla każdego posta.
 */
@Service
public class PostStatsProjectionService {

    /**
     * Repozytorium projekcji statystyk posta.
     *
     * Przechowuje gotowe liczniki:
     * - likeCount,
     * - commentCount,
     * - updatedAt.
     */
    private final PostStatsRepository postStatsRepository;

    /**
     * Repozytorium lajków posta.
     *
     * Używane jako źródło prawdy do przeliczenia aktualnej liczby lajków.
     */
    private final PostLikeRepository postLikeRepository;

    /**
     * Repozytorium komentarzy.
     *
     * Używane jako źródło prawdy do przeliczenia aktualnej liczby komentarzy.
     *
     * Liczymy tylko komentarze nieusunięte logicznie.
     */
    private final CommentRepository commentRepository;

    /**
     * Wstrzyknięcie repozytoriów potrzebnych do odświeżenia projekcji.
     */
    public PostStatsProjectionService(
            PostStatsRepository postStatsRepository,
            PostLikeRepository postLikeRepository,
            CommentRepository commentRepository
    ) {
        this.postStatsRepository = postStatsRepository;
        this.postLikeRepository = postLikeRepository;
        this.commentRepository = commentRepository;
    }

    /**
     * Odświeża statystyki konkretnego posta.
     *
     * Wywoływane zwykle po eventach:
     * - POST_CREATED,
     * - POST_LIKED,
     * - POST_UNLIKED,
     * - COMMENT_CREATED,
     * - COMMENT_DELETED.
     *
     * Flow:
     * 1. policz aktualną liczbę lajków,
     * 2. policz aktualną liczbę aktywnych komentarzy,
     * 3. zapisz / nadpisz rekord w post_stats.
     *
     * Metoda działa w transakcji, żeby zapis projekcji był spójny
     * w ramach jednego odświeżenia.
     */
    @Transactional
    public void refreshStats(UUID postId) {
        /*
         * Liczymy aktualną liczbę lajków posta.
         *
         * post_likes jest źródłem prawdy,
         * a post_stats przechowuje tylko zmaterializowaną kopię licznika.
         */
        long likeCount = postLikeRepository.countByIdPostId(postId);

        /*
         * Liczymy aktualną liczbę komentarzy.
         *
         * Warunek DeletedAtIsNull oznacza, że komentarze usunięte logicznie
         * nie powinny być widoczne w liczniku.
         */
        long commentCount = commentRepository.countByPostIdAndDeletedAtIsNull(postId);

        /*
         * Upsert projekcji.
         *
         * Jeśli rekord statystyk jeszcze nie istnieje, zostanie utworzony.
         * Jeśli istnieje, zostanie zaktualizowany aktualnymi licznikami.
         *
         * updatedAt pokazuje, kiedy projekcja została ostatnio przeliczona.
         */
        postStatsRepository.upsert(
                postId,
                likeCount,
                commentCount,
                Instant.now()
        );
    }
}