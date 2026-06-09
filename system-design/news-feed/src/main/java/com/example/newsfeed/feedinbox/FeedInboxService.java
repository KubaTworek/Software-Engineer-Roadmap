package com.example.newsfeed.feedinbox;

import com.example.newsfeed.follow.FollowRepository;
import com.example.newsfeed.post.Post;
import com.example.newsfeed.post.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za klasyczny feed inbox oparty o fan-out on write.
 *
 * Feed inbox działa jak gotowa lista postów dla użytkownika.
 *
 * Zamiast przy każdym odczycie feedu liczyć:
 * - kogo użytkownik obserwuje,
 * - jakie posty opublikowali obserwowani autorzy,
 * - co jest najnowsze,
 *
 * system zapisuje wpisy do feed_inbox już w momencie publikacji posta.
 *
 * Dzięki temu odczyt feedu jest szybki:
 * FeedService czyta gotowe rekordy z feed_inbox.
 */
@Service
public class FeedInboxService {

    /**
     * Repozytorium feed inbox.
     *
     * Odpowiada za fizyczny zapis wpisów:
     * userId + postId + authorId + source + score + createdAt.
     */
    private final FeedInboxRepository feedInboxRepository;

    /**
     * Repozytorium relacji follow.
     *
     * Używane do znalezienia wszystkich użytkowników,
     * którzy obserwują autora posta.
     */
    private final FollowRepository followRepository;

    /**
     * Repozytorium postów.
     *
     * Używane przy backfillu po follow:
     * gdy użytkownik zaczyna obserwować autora,
     * możemy dodać do jego feedu ostatnie posty tego autora.
     */
    private final PostRepository postRepository;

    /**
     * Limit liczby postów dodawanych do feedu po nowym follow.
     *
     * Konfiguracja:
     *
     * newsfeed:
     *   workers:
     *     fanout:
     *       follow-backfill-limit: 50
     *
     * Dzięki temu follow nie powoduje zbyt dużego jednorazowego zapisu.
     */
    private final int followBackfillLimit;

    /**
     * Wstrzyknięcie zależności serwisu feed inbox.
     */
    public FeedInboxService(
            FeedInboxRepository feedInboxRepository,
            FollowRepository followRepository,
            PostRepository postRepository,
            @Value("${newsfeed.workers.fanout.follow-backfill-limit:50}") int followBackfillLimit
    ) {
        this.feedInboxRepository = feedInboxRepository;
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.followBackfillLimit = followBackfillLimit;
    }

    /**
     * Rozprowadza nowy post do feedów odbiorców.
     *
     * Wywoływane po evencie POST_CREATED.
     *
     * Flow:
     * 1. pobierz followersów autora,
     * 2. dodaj autora jako odbiorcę własnego posta,
     * 3. dla każdego odbiorcy dodaj wpis do feed_inbox,
     * 4. użyj insertIgnore, żeby fan-out był idempotentny.
     *
     * Zwraca liczbę faktycznie dodanych wpisów.
     */
    @Transactional
    public int fanoutPostCreated(UUID postId, UUID authorId, Instant createdAt) {
        /*
         * Pobieramy wszystkich followersów autora.
         *
         * To oni powinni zobaczyć nowy post w swoim feedzie.
         */
        Set<UUID> recipients = new HashSet<>(
                followRepository.findFollowerIds(authorId)
        );

        /*
         * Autor też powinien zobaczyć własny post w swoim feedzie.
         *
         * Set automatycznie usuwa duplikat, jeśli autor byłby już na liście.
         */
        recipients.add(authorId);

        int inserted = 0;

        /*
         * Dla każdego odbiorcy tworzymy wpis w feed_inbox.
         */
        for (UUID recipientId : recipients) {
            inserted += feedInboxRepository.insertIgnore(
                    recipientId,
                    postId,
                    authorId,
                    recipientId.equals(authorId) ? "own_post" : "follow",
                    createdAt.toEpochMilli(),
                    createdAt
            );
        }

        /*
         * Liczba dodanych wpisów może być mniejsza niż liczba recipients,
         * jeśli część rekordów już istniała.
         *
         * To normalne przy retry eventów outbox/Kafka.
         */
        return inserted;
    }

    /**
     * Usuwa post ze wszystkich feedów.
     *
     * Wywoływane po evencie POST_DELETED.
     *
     * Dzięki temu usunięty post nie będzie dalej widoczny
     * w feed inbox użytkowników.
     */
    @Transactional
    public int removePostFromFeeds(UUID postId) {
        /*
         * Usuwamy wszystkie wpisy feed_inbox wskazujące na ten post.
         */
        return feedInboxRepository.deleteByPostId(postId);
    }

    /**
     * Backfill feedu po nowym follow.
     *
     * Gdy użytkownik zaczyna obserwować autora, jego feed nie powinien być pusty
     * względem tego autora aż do kolejnego nowego posta.
     *
     * Dlatego dodajemy do feedu kilka ostatnich postów followee.
     *
     * Przykład:
     * - user A zaczyna obserwować autora B,
     * - system pobiera ostatnie 50 postów B,
     * - dodaje je do feed_inbox A ze źródłem follow_backfill.
     */
    @Transactional
    public int backfillFollow(UUID followerId, UUID followeeId) {
        /*
         * Pobieramy ostatnie posty autora.
         *
         * Limit chroni system przed dużym kosztem,
         * jeśli autor ma bardzo długą historię postów.
         */
        List<Post> recentPosts = postRepository.findLatestPostsByAuthor(
                followeeId,
                PageRequest.of(0, followBackfillLimit)
        );

        int inserted = 0;

        /*
         * Dodajemy ostatnie posty autora do feedu nowego followera.
         */
        for (Post post : recentPosts) {
            inserted += feedInboxRepository.insertIgnore(
                    followerId,
                    post.getId(),
                    followeeId,
                    "follow_backfill",
                    post.getCreatedAt().toEpochMilli(),
                    post.getCreatedAt()
            );
        }

        /*
         * Zwracamy liczbę faktycznie dodanych wpisów.
         *
         * insertIgnore zabezpiecza przed duplikatami,
         * np. jeśli użytkownik follow/unfollow/follow wykona kilka razy.
         */
        return inserted;
    }

    /**
     * Usuwa posty danego autora z feedu konkretnego użytkownika.
     *
     * Wywoływane zwykle po unfollow.
     *
     * Dzięki temu po przestaniu obserwowania autora
     * użytkownik nie będzie dalej widział jego postów z feed_inbox.
     */
    @Transactional
    public int removeAuthorFromUserFeed(UUID userId, UUID authorId) {
        /*
         * Usuwamy tylko wpisy konkretnego autora z feedu konkretnego użytkownika.
         *
         * Nie dotykamy feedów innych użytkowników.
         */
        return feedInboxRepository.deleteByUserIdAndAuthorId(
                userId,
                authorId
        );
    }
}