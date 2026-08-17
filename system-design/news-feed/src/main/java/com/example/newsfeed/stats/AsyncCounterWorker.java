package com.example.newsfeed.stats;

import com.example.newsfeed.events.DomainEvent;
import com.example.newsfeed.events.IdempotentEventProcessor;
import com.example.newsfeed.events.NewsFeedTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Worker asynchroniczny odpowiedzialny za aktualizację liczników posta.
 *
 * Obsługuje eventy związane z:
 * - lajkami,
 * - usunięciem lajków,
 * - dodaniem komentarza,
 * - usunięciem komentarza.
 *
 * Zamiast za każdym razem aktualizować jeden rekord licznika posta,
 * worker zapisuje zmiany do shardowanych liczników.
 *
 * To ogranicza problem hot row:
 * bardzo popularny post nie dostaje tysięcy update'ów na jeden rekord,
 * tylko update'y są rozkładane na wiele shardów.
 */
@Service
public class AsyncCounterWorker {

    /**
     * Liczba shardów licznika.
     *
     * Jeden post ma wiele shardów dla danego licznika, np.:
     * - likes shard 0,
     * - likes shard 1,
     * - likes shard 2,
     * - ...
     *
     * Finalna wartość licznika to suma wszystkich shardów.
     */
    private static final int COUNTER_SHARDS = 32;

    /**
     * Repozytorium shardowanych liczników.
     *
     * Odpowiada za atomowe zwiększanie lub zmniejszanie wartości
     * konkretnego sharda licznika.
     */
    private final CounterShardRepository counterShardRepository;

    /**
     * Procesor idempotencji eventów.
     *
     * Kafka może dostarczyć ten sam event więcej niż raz.
     *
     * Bez idempotencji ten sam like albo komentarz mógłby zostać policzony
     * kilkukrotnie.
     */
    private final IdempotentEventProcessor idempotentEventProcessor;

    /**
     * Wstrzyknięcie repozytorium liczników i procesora idempotencji.
     */
    public AsyncCounterWorker(
            CounterShardRepository counterShardRepository,
            IdempotentEventProcessor idempotentEventProcessor
    ) {
        this.counterShardRepository = counterShardRepository;
        this.idempotentEventProcessor = idempotentEventProcessor;
    }

    /**
     * Konsumuje eventy wpływające na liczniki posta.
     *
     * Topic list:
     * - POST_LIKED zwiększa licznik lajków,
     * - POST_UNLIKED zmniejsza licznik lajków,
     * - COMMENT_CREATED zwiększa licznik komentarzy,
     * - COMMENT_DELETED zmniejsza licznik komentarzy.
     *
     * Worker ma osobną consumer group:
     * news-feed-counters
     *
     * Dzięki temu liczniki mogą być przetwarzane niezależnie
     * od fan-outu, feedu czy innych workerów.
     */
    @KafkaListener(topics = {
            NewsFeedTopics.POST_LIKED,
            NewsFeedTopics.POST_UNLIKED,
            NewsFeedTopics.COMMENT_CREATED,
            NewsFeedTopics.COMMENT_DELETED
    }, groupId = "news-feed-counters")
    public void onCounterEvent(DomainEvent event, Acknowledgment acknowledgment) {
        /*
         * processOnce gwarantuje idempotencję efektu.
         *
         * Jeśli ten sam event zostanie dostarczony drugi raz,
         * applyCounterEvent nie powinno wykonać się ponownie.
         */
        idempotentEventProcessor.processOnce(
                event,
                () -> applyCounterEvent(event)
        );

        /*
         * Potwierdzamy offset Kafki po zakończeniu przetwarzania.
         *
         * Jeśli przed ack poleci wyjątek, Kafka może ponowić event.
         */
        acknowledgment.acknowledge();
    }

    /**
     * Przelicza event domenowy na zmianę konkretnego licznika.
     *
     * Nie zapisuje pełnej projekcji statystyk posta.
     * Zapisuje tylko deltę do odpowiedniego shardu.
     *
     * Przykład:
     * POST_LIKED -> counterName = likes, delta = +1
     * POST_UNLIKED -> counterName = likes, delta = -1
     */
    @Transactional
    public void applyCounterEvent(DomainEvent event) {
        String counterName;
        long delta;

        /*
         * Mapujemy typ eventu na nazwę licznika i zmianę wartości.
         */
        switch (event.eventType()) {
            case NewsFeedTopics.POST_LIKED -> {
                counterName = "likes";
                delta = 1;
            }
            case NewsFeedTopics.POST_UNLIKED -> {
                counterName = "likes";
                delta = -1;
            }
            case NewsFeedTopics.COMMENT_CREATED -> {
                counterName = "comments";
                delta = 1;
            }
            case NewsFeedTopics.COMMENT_DELETED -> {
                counterName = "comments";
                delta = -1;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported counter event: " + event.eventType()
            );
        }

        /*
         * Wyciągamy postId z attributes eventu.
         *
         * Jeśli go nie ma, fallbackujemy do entityId eventu.
         *
         * Dzięki temu worker jest odporniejszy na różne formaty eventów,
         * ale produkcyjnie najlepiej wymagać jawnego postId w payloadzie.
         */
        UUID postId = UUID.fromString(
                event.attributes().getOrDefault(
                        "postId",
                        event.entityId().toString()
                )
        );

        /*
         * Wybieramy losowy shard licznika.
         *
         * To rozkłada zapis na 32 rekordy zamiast jednego.
         *
         * Dla popularnego posta wiele eventów like/comment nie uderza
         * w ten sam wiersz, tylko rozprasza się po shardach.
         */
        int shard = ThreadLocalRandom.current().nextInt(COUNTER_SHARDS);

        /*
         * Atomowo zwiększamy albo zmniejszamy licznik w konkretnym shardzie.
         *
         * Klucz logiczny:
         * - entityType = "post",
         * - entityId = postId,
         * - counterName = likes/comments,
         * - shard = losowy shard.
         *
         * Finalna liczba lajków albo komentarzy powstaje przez zsumowanie
         * wszystkich shardów dla danego counterName.
         */
        counterShardRepository.increment(
                "post",
                postId,
                counterName,
                shard,
                delta,
                Instant.now()
        );
    }
}