package com.example.newsfeed.fanout;

import com.example.newsfeed.celebrity.CelebrityService;
import com.example.newsfeed.events.DomainEvent;
import com.example.newsfeed.events.IdempotentEventProcessor;
import com.example.newsfeed.events.NewsFeedTopics;
import com.example.newsfeed.feed.FeedInboxItem;
import com.example.newsfeed.feed.FeedStorage;
import com.example.newsfeed.follow.FollowRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Worker asynchroniczny odpowiedzialny za fan-out postów do feedów użytkowników.
 *
 * Fan-out oznacza:
 * kiedy autor publikuje post, system zapisuje referencję do tego posta
 * w feed_inbox użytkowników, którzy obserwują autora.
 *
 * Dzięki temu odczyt feedu jest szybki:
 * FeedService nie musi za każdym razem liczyć całego grafu follow,
 * tylko czyta gotową listę postów z feed storage.
 *
 * Ten worker reaguje na eventy z Kafki:
 * - POST_CREATED: dystrybuuje post do feedów,
 * - POST_DELETED: usuwa referencje do posta.
 */
@Service
public class FanoutWorker {

    /**
     * Zapewnia idempotencję przetwarzania eventów.
     *
     * Kafka może dostarczyć ten sam event więcej niż raz.
     * Bez idempotencji moglibyśmy:
     * - dodać ten sam post kilka razy do feed_inbox,
     * - kilka razy zwiększyć metryki,
     * - kilka razy wykonać kosztowny fan-out.
     *
     * processOnce pilnuje, żeby event o tym samym eventId
     * został przetworzony tylko raz.
     */
    private final IdempotentEventProcessor idempotentEventProcessor;

    /**
     * Abstrakcja zapisu feed_inbox.
     *
     * Worker nie wie, czy feed jest trzymany w PostgreSQL, DynamoDB czy Cassandrze.
     * To pozwala wymienić storage bez zmiany logiki fan-outu.
     */
    private final FeedStorage feedStorage;

    /**
     * Repozytorium shardów followersów.
     *
     * Followersów autora dzielimy na shardy, żeby fan-out dało się skalować.
     * Zamiast przetwarzać jedną ogromną listę followersów,
     * worker przechodzi shard po shardzie.
     */
    private final FollowerShardRepository followerShardRepository;

    /**
     * Serwis shardowania followersów.
     *
     * Dostarcza m.in. liczbę shardów.
     * Ta liczba decyduje, po ilu shardach worker musi przejść przy fan-oucie.
     */
    private final FollowerShardService followerShardService;

    /**
     * Repozytorium grafu follow.
     *
     * Tutaj używane głównie do policzenia liczby followersów autora.
     * Ta liczba decyduje, czy autor powinien zostać potraktowany jako celebrity.
     */
    private final FollowRepository followRepository;

    /**
     * Serwis obsługujący celebrity pull model.
     *
     * Dla bardzo popularnych autorów nie robimy masowego fan-outu.
     * Zamiast zapisać post do feedów milionów osób,
     * zapisujemy go raz w celebrity_posts i dociągamy przy odczycie feedu.
     */
    private final CelebrityService celebrityService;

    /**
     * Maksymalna liczba followersów pobieranych z jednego sharda.
     *
     * Konfiguracja:
     * newsfeed.fanout.batch-size
     *
     * Chroni workera przed zbyt dużym jednorazowym przetwarzaniem.
     */
    private final int batchSize;

    /**
     * Metryka liczby obsłużonych eventów fan-out.
     *
     * Przydatna w Prometheusie do monitorowania przepływu eventów.
     */
    private final Counter fanoutEvents;

    /**
     * Metryka liczby wpisów zapisanych do feed_inbox.
     *
     * Pokazuje realny koszt fan-outu.
     * Jeden event POST_CREATED może wygenerować wiele FeedInboxItem.
     */
    private final Counter fanoutItems;

    /**
     * Konstruktor workera.
     *
     * Wstrzykuje:
     * - procesor idempotencji,
     * - feed storage,
     * - repozytoria shardów i follow graphu,
     * - celebrity service,
     * - batch size,
     * - meter registry do metryk.
     */
    public FanoutWorker(
            IdempotentEventProcessor idempotentEventProcessor,
            FeedStorage feedStorage,
            FollowerShardRepository followerShardRepository,
            FollowerShardService followerShardService,
            FollowRepository followRepository,
            CelebrityService celebrityService,
            @Value("${newsfeed.fanout.batch-size:500}") int batchSize,
            MeterRegistry meterRegistry
    ) {
        this.idempotentEventProcessor = idempotentEventProcessor;
        this.feedStorage = feedStorage;
        this.followerShardRepository = followerShardRepository;
        this.followerShardService = followerShardService;
        this.followRepository = followRepository;
        this.celebrityService = celebrityService;
        this.batchSize = batchSize;

        /*
         * Licznik obsłużonych eventów POST_CREATED / POST_DELETED.
         */
        this.fanoutEvents = Counter.builder("newsfeed.fanout.events")
                .register(meterRegistry);

        /*
         * Licznik wszystkich wpisów dodanych do feed_inbox.
         */
        this.fanoutItems = Counter.builder("newsfeed.fanout.items")
                .register(meterRegistry);
    }

    /**
     * Obsługuje event utworzenia posta.
     *
     * Topic:
     * newsfeed.post.created
     *
     * Główne zadanie:
     * zdecydować, gdzie ma trafić nowy post.
     *
     * Możliwe ścieżki:
     * - zawsze do inboxa autora,
     * - do celebrity_posts, jeśli autor jest celebrytą,
     * - do feed_inbox followersów, jeśli autor nie jest celebrytą.
     */
    @KafkaListener(topics = NewsFeedTopics.POST_CREATED, groupId = "news-feed-fanout")
    public void onPostCreated(DomainEvent event, Acknowledgment acknowledgment) {
        /*
         * Przetwarzamy event tylko raz.
         *
         * Jeśli Kafka dostarczy event ponownie, processOnce powinien wykryć,
         * że eventId był już obsłużony, i nie wykonać lambdy drugi raz.
         */
        idempotentEventProcessor.processOnce(event, () -> {
            /*
             * ID posta znajduje się w entityId eventu.
             */
            UUID postId = event.entityId();

            /*
             * authorId i createdAt bierzemy z attributes.
             *
             * Dzięki temu worker nie musi robić SELECT-a po Post,
             * żeby wykonać podstawowy fan-out.
             */
            UUID authorId = UUID.fromString(event.attributes().get("authorId"));
            Instant createdAt = Instant.parse(event.attributes().get("createdAt"));

            /*
             * Liczymy followersów autora.
             *
             * To służy do decyzji, czy autor przekroczył próg celebrity.
             *
             * Uwaga produkcyjna:
             * przy dużej skali ten count powinien pochodzić z projekcji/licznika,
             * a nie z liczenia relacji follow przy każdym poście.
             */
            long followerCount = followRepository.countByIdFolloweeId(authorId);

            /*
             * Aktualizujemy status celebrity.
             *
             * Jeśli autor przekroczył threshold, zostanie oznaczony jako celebrity.
             */
            celebrityService.refreshCelebrityStatus(authorId, followerCount);

            /*
             * Autor zawsze dostaje własny post do swojego feed_inbox.
             *
             * Dzięki temu po publikacji widzi swój post w feedzie,
             * nawet jeśli nie ma relacji follow do samego siebie.
             */
            feedStorage.appendIdempotent(List.of(new FeedInboxItem(
                    authorId,
                    postId,
                    authorId,
                    createdAt.toEpochMilli(),
                    "OWN",
                    0,
                    createdAt
            )));

            /*
             * Celebrity pull model.
             *
             * Jeśli autor jest celebrytą, kończymy klasyczny fan-out.
             *
             * Nie zapisujemy posta do feed_inbox każdego followera,
             * bo to mogłoby oznaczać miliony zapisów dla jednego posta.
             *
             * Zamiast tego zapisujemy post do celebrity_posts.
             * FeedService dociągnie go przy odczycie feedu użytkownika.
             */
            if (celebrityService.isCelebrity(authorId)) {
                celebrityService.addCelebrityPost(
                        authorId,
                        postId,
                        createdAt
                );

                /*
                 * Event został obsłużony.
                 *
                 * Nie zwiększamy fanoutItems, bo nie robiliśmy masowego fan-outu.
                 */
                fanoutEvents.increment();
                return;
            }

            /*
             * Klasyczny fan-out on write dla zwykłych autorów.
             *
             * Przechodzimy po shardach followersów autora.
             */
            for (int shard = 0; shard < followerShardService.shardCount(); shard++) {
                /*
                 * Pobieramy followersów z konkretnego sharda.
                 *
                 * PageRequest.of(0, batchSize) pobiera tylko pierwszą paczkę.
                 *
                 * Ważna uwaga:
                 * jeśli shard ma więcej niż batchSize followersów,
                 * ta wersja nie obsłuży reszty.
                 *
                 * Produkcyjnie trzeba dodać paginację po shardzie
                 * albo emitować osobne taski fanout-shard-page.
                 */
                List<FollowerShard> followers = followerShardRepository.findByFolloweeIdAndShardId(
                        authorId,
                        shard,
                        PageRequest.of(0, batchSize)
                );

                /*
                 * Zamieniamy followersów na wpisy feed_inbox.
                 *
                 * Każdy FeedInboxItem mówi:
                 * "postId autora authorId powinien pojawić się w feedzie followerId".
                 */
                List<FeedInboxItem> items = followers.stream()
                        .map(row -> new FeedInboxItem(
                                row.getFollowerId(),
                                postId,
                                authorId,
                                createdAt.toEpochMilli(),
                                "FOLLOWING",
                                row.getShardId(),
                                createdAt
                        ))
                        .toList();

                /*
                 * Idempotentny zapis do feed storage.
                 *
                 * Jeśli worker powtórzy fan-out, storage powinien zignorować duplikaty.
                 */
                feedStorage.appendIdempotent(items);

                /*
                 * Metryka liczby zapisanych elementów feedu.
                 */
                fanoutItems.increment(items.size());
            }

            /*
             * Metryka obsłużonego eventu fan-out.
             */
            fanoutEvents.increment();
        });

        /*
         * Potwierdzamy offset Kafki dopiero po zakończeniu przetwarzania.
         *
         * Jeśli przed tym miejscem poleci wyjątek, event może zostać dostarczony ponownie.
         */
        acknowledgment.acknowledge();
    }

    /**
     * Obsługuje event usunięcia posta.
     *
     * Topic:
     * newsfeed.post.deleted
     *
     * Celem jest usunięcie posta z miejsc, z których mógłby nadal trafić do feedu:
     * - feed storage,
     * - celebrity_posts.
     */
    @KafkaListener(topics = NewsFeedTopics.POST_DELETED, groupId = "news-feed-fanout")
    public void onPostDeleted(DomainEvent event, Acknowledgment acknowledgment) {
        /*
         * Tak jak przy create, delete też musi być idempotentny.
         *
         * Ten sam event deleted może zostać dostarczony więcej niż raz.
         */
        idempotentEventProcessor.processOnce(event, () -> {
            /*
             * Usuwamy post z feed storage.
             *
             * W PostgreSQL może to być delete po post_id.
             *
             * W DynamoDB zwykle robi się to inaczej:
             * - tombstone,
             * - reverse index,
             * - lazy filtering podczas hydratacji,
             * bo globalne usuwanie po postId nie jest tanie.
             */
            feedStorage.removePost(event.entityId());

            /*
             * Jeśli post był postem celebryty, usuwamy go również z celebrity_posts.
             *
             * Dzięki temu FeedService nie dociągnie go już w celebrity pull model.
             */
            celebrityService.removeCelebrityPost(event.entityId());
        });

        /*
         * Ack po cleanupie.
         */
        acknowledgment.acknowledge();
    }
}