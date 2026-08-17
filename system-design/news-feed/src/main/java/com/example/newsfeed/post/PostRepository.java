package com.example.newsfeed.post;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    /**
     * Pobiera aktywny post po ID.
     *
     * deletedAt IS NULL oznacza, że ignorujemy posty usunięte przez soft delete.
     */
    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Pobiera wiele aktywnych postów po ID.
     *
     * Używane np. przy hydratacji feedu, kiedy feed_inbox trzyma same postId,
     * a szczegóły posta trzeba dociągnąć z tabeli posts.
     */
    List<Post> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    /**
     * Pobiera pierwszą stronę globalnego feedu.
     *
     * JOIN FETCH p.author zapobiega problemowi N+1,
     * bo autorzy postów są pobierani razem z postami.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author " +
            "WHERE p.deletedAt IS NULL " +
            "ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findFirstGlobalPage(Pageable pageable);

    /**
     * Pobiera kolejną stronę globalnego feedu przy użyciu cursor pagination.
     *
     * Cursor składa się z:
     * - createdAt ostatniego posta z poprzedniej strony,
     * - id ostatniego posta z poprzedniej strony.
     *
     * Dzięki temu unikamy offset pagination.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author " +
            "WHERE p.deletedAt IS NULL " +
            "AND (p.createdAt < :createdAt OR (p.createdAt = :createdAt AND p.id < :id)) " +
            "ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findNextGlobalPage(Instant createdAt, UUID id, Pageable pageable);

    /**
     * Pobiera posty używane do zbudowania feedu.
     *
     * Feed często trzyma tylko listę ID-ków kandydatów.
     * Ta metoda zamienia ID-ki na pełne encje Post + author.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author " +
            "WHERE p.deletedAt IS NULL " +
            "AND p.id IN :ids")
    List<Post> findFeedPostsByIds(Collection<UUID> ids);

    /**
     * Pobiera najnowsze posty konkretnego autora.
     *
     * Używane np. przy:
     * - backfillu feedu po follow,
     * - celebrity pull model,
     * - rekomendacjach opartych o autora.
     *
     * Parametr followeeId to ID autora, którego posty pobieramy.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author " +
            "WHERE p.deletedAt IS NULL " +
            "AND p.author.id = :followeeId " +
            "ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findLatestPostsByAuthor(UUID followeeId, Pageable pageable);

    /**
     * Pobiera kandydatów do sekcji trending.
     *
     * Na tym etapie nie mamy jeszcze osobnej tabeli trending_score,
     * więc robimy prosty fallback:
     * - tylko aktywne posty,
     * - najnowsze jako kandydaci do dalszego scoringu/rankingu.
     *
     * W bardziej produkcyjnej wersji ta metoda powinna sortować po:
     * - liczbie lajków,
     * - komentarzach,
     * - CTR,
     * - velocity engagementu,
     * - score z post_features albo post_stats.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author " +
            "WHERE p.deletedAt IS NULL " +
            "ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findTrendingCandidates(Pageable pageable);
}