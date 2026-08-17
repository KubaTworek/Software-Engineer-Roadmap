package com.example.newsfeed.fanout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za shardowanie followersów autora.
 *
 * Problem:
 * autor może mieć bardzo wielu followersów.
 * Jeśli trzymalibyśmy ich jako jedną dużą listę, fan-out jednego posta
 * byłby trudny do skalowania i dzielenia na mniejsze paczki pracy.
 *
 * Rozwiązanie:
 * followersów dzielimy na shardy.
 *
 * Każdy follower trafia do jednego sharda wyliczonego z jego followerId.
 * FanoutWorker może potem przetwarzać followersów autora shard po shardzie.
 *
 * Dzięki temu fan-out jest bardziej przewidywalny i łatwiejszy do równoleglenia.
 */
@Service
public class FollowerShardService {

    /**
     * Repozytorium shardów followersów.
     *
     * Przechowuje relację:
     * followeeId + shardId + followerId.
     *
     * Czyli:
     * "ten follower obserwuje tego autora i należy do tego sharda".
     */
    private final FollowerShardRepository repository;

    /**
     * Liczba shardów, na które dzielimy followersów każdego autora.
     *
     * Konfiguracja:
     *
     * newsfeed:
     *   fanout:
     *     follower-shards: 32
     *
     * Więcej shardów oznacza:
     * - mniejsze paczki followersów,
     * - łatwiejsze równoleglenie,
     * - ale więcej zapytań podczas fan-outu.
     */
    private final int shardCount;

    /**
     * Wstrzyknięcie repozytorium i konfiguracji liczby shardów.
     */
    public FollowerShardService(
            FollowerShardRepository repository,
            @Value("${newsfeed.fanout.follower-shards:32}") int shardCount
    ) {
        this.repository = repository;
        this.shardCount = shardCount;
    }

    /**
     * Dodaje followera do odpowiedniego sharda autora.
     *
     * Wywoływane zwykle po utworzeniu relacji follow.
     *
     * Flow:
     * 1. użytkownik A zaczyna obserwować autora B,
     * 2. FollowService zapisuje relację follow,
     * 3. FollowerShardService zapisuje followera A w shardzie autora B,
     * 4. przy następnym poście autora B FanoutWorker znajdzie A w tym shardzie.
     */
    @Transactional
    public void addFollower(UUID followeeId, UUID followerId) {
        /*
         * Wyliczamy shard na podstawie followerId.
         *
         * Ten sam follower zawsze trafi do tego samego sharda,
         * o ile shardCount się nie zmieni.
         */
        int shard = shardFor(followerId);

        /*
         * Zapisujemy wpis sharda.
         *
         * followeeId = autor obserwowany,
         * followerId = użytkownik obserwujący,
         * shard = bucket, w którym znajduje się follower.
         */
        repository.save(
                new FollowerShard(
                        followeeId,
                        shard,
                        followerId,
                        Instant.now()
                )
        );
    }

    /**
     * Usuwa followera z shardów autora.
     *
     * Wywoływane zwykle po unfollow.
     *
     * Dzięki temu kolejne posty autora nie będą już fan-outowane
     * do feedu użytkownika, który przestał obserwować autora.
     */
    @Transactional
    public void removeFollower(UUID followeeId, UUID followerId) {
        /*
         * Usuwamy relację po followeeId i followerId.
         *
         * Nie musimy znać shardId, jeśli repozytorium potrafi usunąć wpis
         * po parze autor-obserwujący.
         */
        repository.deleteByFolloweeIdAndFollowerId(
                followeeId,
                followerId
        );
    }

    /**
     * Wylicza shard dla followera.
     *
     * Shard zależy od followerId, nie od followeeId.
     *
     * Dzięki temu rozkład followersów autora powinien być względnie równy
     * między shardami.
     */
    public int shardFor(UUID followerId) {
        /*
         * UUID.hashCode() może być ujemny.
         *
         * Math.floorMod daje wynik zawsze w zakresie:
         * 0 <= shard < shardCount.
         */
        return Math.floorMod(
                followerId.hashCode(),
                shardCount
        );
    }

    /**
     * Zwraca liczbę shardów używaną przez fan-out.
     *
     * FanoutWorker używa tego, żeby wiedzieć,
     * po ilu shardach followersów ma przejść.
     */
    public int shardCount() {
        return shardCount;
    }
}