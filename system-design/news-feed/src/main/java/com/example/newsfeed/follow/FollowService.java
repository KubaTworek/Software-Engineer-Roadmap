package com.example.newsfeed.follow;

import com.example.newsfeed.common.ConflictException;
import com.example.newsfeed.common.NotFoundException;
import com.example.newsfeed.events.DomainEvent;
import com.example.newsfeed.events.KafkaEventPublisher;
import com.example.newsfeed.events.NewsFeedTopics;
import com.example.newsfeed.fanout.FollowerShardService;
import com.example.newsfeed.feed.FeedCacheService;
import com.example.newsfeed.user.User;
import com.example.newsfeed.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Serwis biznesowy odpowiedzialny za relacje obserwowania użytkowników.
 *
 * To jest kluczowy element systemu News Feed, bo graf follow decyduje,
 * czyje posty powinny trafiać do spersonalizowanego feedu użytkownika.
 *
 * Ten serwis odpowiada za:
 * - utworzenie relacji follower -> followee,
 * - usunięcie relacji,
 * - walidację, że użytkownik nie obserwuje samego siebie,
 * - ochronę przed duplikatem follow,
 * - aktualizację shardów followersów,
 * - czyszczenie cache feedu,
 * - publikację eventów do Kafki,
 * - zwracanie statystyk followers/following.
 */
@Service
public class FollowService {

    /**
     * Repozytorium relacji follow.
     *
     * Przechowuje graf obserwacji:
     * follower_id -> followee_id.
     *
     * To jest podstawowe źródło prawdy mówiące,
     * kto kogo obserwuje.
     */
    private final FollowRepository followRepository;

    /**
     * Repozytorium użytkowników.
     *
     * Służy do sprawdzenia, czy follower i followee istnieją
     * oraz do pobrania pełnych encji User zarządzanych przez JPA.
     */
    private final UserRepository userRepository;

    /**
     * Cache feedu użytkownika.
     *
     * Po follow/unfollow spersonalizowany feed użytkownika może się zmienić,
     * więc trzeba go unieważnić.
     */
    private final FeedCacheService feedCacheService;

    /**
     * Serwis shardowania followersów.
     *
     * W dużym systemie autor może mieć miliony obserwujących.
     * Trzymanie jednej płaskiej listy followersów jest problematyczne dla fan-outu.
     *
     * Dlatego relacje są dodatkowo zapisywane w shardach,
     * żeby fan-out worker mógł przetwarzać followersów partiami.
     */
    private final FollowerShardService followerShardService;

    /**
     * Publisher eventów domenowych do Kafki.
     *
     * Follow/unfollow wpływa na wiele części systemu:
     * - feed,
     * - rekomendacje,
     * - statystyki użytkowników,
     * - graf społecznościowy,
     * - analitykę.
     *
     * Dlatego po zmianie relacji publikujemy event.
     */
    private final KafkaEventPublisher eventPublisher;

    /**
     * Wstrzyknięcie zależności przez konstruktor.
     *
     * Serwis potrzebuje:
     * - repozytoriów do operacji synchronicznych,
     * - shard service do skalowania fan-outu,
     * - cache service do invalidacji feedu,
     * - event publishera do procesów asynchronicznych.
     */
    public FollowService(
            FollowRepository followRepository,
            UserRepository userRepository,
            FeedCacheService feedCacheService,
            FollowerShardService followerShardService,
            KafkaEventPublisher eventPublisher
    ) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.feedCacheService = feedCacheService;
        this.followerShardService = followerShardService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Tworzy relację follow między aktualnym użytkownikiem a wybranym użytkownikiem.
     *
     * currentUser = follower
     * followeeId = użytkownik obserwowany
     *
     * Flow:
     * 1. zablokuj follow samego siebie,
     * 2. pobierz followera z bazy,
     * 3. pobierz followee z bazy,
     * 4. sprawdź, czy relacja już istnieje,
     * 5. zapisz relację follow,
     * 6. dodaj followera do odpowiedniego sharda,
     * 7. wyczyść cache feedu followera,
     * 8. opublikuj event follow.created,
     * 9. zwróć DTO relacji.
     */
    @Transactional
    public FollowResponse follow(User currentUser, UUID followeeId) {
        /*
         * Nie pozwalamy obserwować samego siebie.
         *
         * Taka relacja nie ma sensu biznesowego i zaburzałaby feed,
         * statystyki oraz rekomendacje.
         */
        if (currentUser.getId().equals(followeeId)) {
            throw new ConflictException("You cannot follow yourself.");
        }

        /*
         * Pobieramy followera z bazy.
         *
         * currentUser pochodzi z warstwy auth,
         * ale tutaj chcemy mieć encję User zarządzaną przez JPA.
         */
        User follower = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new NotFoundException("Follower not found."));

        /*
         * Pobieramy użytkownika, który ma być obserwowany.
         *
         * Jeśli nie istnieje, nie tworzymy relacji follow.
         */
        User followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new NotFoundException("User to follow not found."));

        /*
         * Klucz złożony relacji follow.
         *
         * Jedna relacja jest jednoznacznie określona przez:
         * followerId + followeeId.
         */
        FollowId id = new FollowId(follower.getId(), followee.getId());

        /*
         * Ochrona przed duplikatem.
         *
         * Użytkownik nie może obserwować tej samej osoby dwa razy.
         * Bez tego feed i liczniki mogłyby zostać zduplikowane.
         */
        if (followRepository.existsById(id)) {
            throw new ConflictException("You already follow this user.");
        }

        /*
         * Zapis relacji follow w głównej tabeli.
         *
         * To jest źródło prawdy grafu społecznościowego.
         */
        Follow follow = followRepository.save(
                new Follow(id, follower, followee, Instant.now())
        );

        /*
         * Zapis followera do sharda followee.
         *
         * To przyspiesza fan-out on write.
         *
         * Gdy followee opublikuje post, fan-out worker może pobierać followersów
         * shard po shardzie, zamiast skanować jedną ogromną listę.
         */
        followerShardService.addFollower(
                followee.getId(),
                follower.getId()
        );

        /*
         * Invalidacja cache feedu followera.
         *
         * Po follow użytkownik powinien zacząć widzieć posty nowo obserwowanej osoby,
         * więc jego obecny cached feed może być nieaktualny.
         */
        feedCacheService.evictPersonalizedFeed(currentUser.getId());

        /*
         * Event follow.created.
         *
         * Konsumenci mogą na tej podstawie:
         * - wykonać backfill ostatnich postów followee do feedu followera,
         * - zaktualizować rekomendacje,
         * - przeliczyć statystyki followers/following,
         * - zapisać sygnał społecznościowy dla ML.
         */
        eventPublisher.publish(
                NewsFeedTopics.FOLLOW_CREATED,
                follower.getId().toString(),
                DomainEvent.of(
                        NewsFeedTopics.FOLLOW_CREATED,
                        follower.getId(),
                        followee.getId(),
                        Map.of(
                                "followerId", follower.getId().toString(),
                                "followeeId", followee.getId().toString()
                        )
                )
        );

        /*
         * Zwracamy DTO relacji follow.
         *
         * Nie zwracamy encji JPA bezpośrednio, żeby nie ujawniać modelu bazy
         * ani zależności lazy-loading.
         */
        return FollowResponse.from(follow);
    }

    /**
     * Usuwa relację follow między aktualnym użytkownikiem a obserwowanym użytkownikiem.
     *
     * currentUser = follower
     * followeeId = użytkownik, którego przestajemy obserwować
     *
     * Flow:
     * 1. usuń relację z głównej tabeli follow,
     * 2. usuń followera z sharda followee,
     * 3. wyczyść cache feedu followera,
     * 4. opublikuj event follow.deleted.
     */
    @Transactional
    public void unfollow(User currentUser, UUID followeeId) {
        /*
         * Usunięcie relacji follow z głównej tabeli.
         *
         * Ta metoda jest praktycznie idempotentna:
         * jeśli relacja nie istnieje, Spring Data wykona delete bez efektu.
         */
        followRepository.deleteByIdFollowerIdAndIdFolloweeId(
                currentUser.getId(),
                followeeId
        );

        /*
         * Usunięcie followera z sharda.
         *
         * Dzięki temu przyszłe fan-outy postów followee
         * nie będą już trafiały do tego użytkownika.
         */
        followerShardService.removeFollower(
                followeeId,
                currentUser.getId()
        );

        /*
         * Invalidacja cache feedu użytkownika.
         *
         * Po unfollow feed powinien przestać pokazywać nowe treści tej osoby.
         */
        feedCacheService.evictPersonalizedFeed(currentUser.getId());

        /*
         * Event follow.deleted.
         *
         * Konsumenci mogą:
         * - wyczyścić feed_inbox z postów followee,
         * - zaktualizować rekomendacje,
         * - przeliczyć statystyki,
         * - zapisać sygnał negatywnej preferencji.
         *
         * Uwaga: event jest publikowany zawsze, nawet jeśli relacja nie istniała.
         * Jeśli chcesz pełną idempotencję eventów, warto najpierw sprawdzić,
         * czy relacja istniała, i publikować event tylko po realnej zmianie.
         */
        eventPublisher.publish(
                NewsFeedTopics.FOLLOW_DELETED,
                currentUser.getId().toString(),
                DomainEvent.of(
                        NewsFeedTopics.FOLLOW_DELETED,
                        currentUser.getId(),
                        followeeId,
                        Map.of(
                                "followerId", currentUser.getId().toString(),
                                "followeeId", followeeId.toString()
                        )
                )
        );
    }

    /**
     * Zwraca statystyki obserwacji dla użytkownika.
     *
     * Wynik zawiera zwykle:
     * - following: ilu użytkowników obserwuje user,
     * - followers: ilu użytkowników obserwuje usera.
     *
     * Metoda jest readOnly, bo niczego nie zapisuje.
     */
    @Transactional(readOnly = true)
    public FollowStatsResponse getStats(UUID userId) {
        /*
         * Sprawdzamy, czy użytkownik istnieje.
         *
         * Nie zwracamy statystyk dla nieistniejącego profilu.
         */
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found.");
        }

        /*
         * Liczymy relacje follow.
         *
         * countByIdFollowerId:
         * - ile osób obserwuje user.
         *
         * countByIdFolloweeId:
         * - ile osób obserwuje usera.
         *
         * Uwaga produkcyjna:
         * przy dużej skali nie powinno się liczyć tego COUNT-em przy każdym requestcie.
         * Lepiej użyć asynchronicznie aktualizowanych liczników/projekcji.
         */
        return new FollowStatsResponse(
                followRepository.countByIdFollowerId(userId),
                followRepository.countByIdFolloweeId(userId)
        );
    }
}