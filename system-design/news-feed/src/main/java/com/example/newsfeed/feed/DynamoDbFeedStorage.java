package com.example.newsfeed.feed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Produkcyjna implementacja FeedStorage oparta o DynamoDB.
 *
 * DynamoDB pasuje do feed inbox, bo główny access pattern jest prosty:
 *
 * "daj mi najnowsze posty dla userId"
 *
 * Model danych:
 *
 * PK = userId
 * SK = createdAt#postId
 *
 * Dzięki temu odczyt feedu jednego użytkownika to szybkie Query po partycji,
 * bez joinów i bez offset pagination.
 */
@Service
@ConditionalOnProperty(
        name = "newsfeed.feed.storage",
        havingValue = "dynamodb"
)
public class DynamoDbFeedStorage implements FeedStorage {

    private static final int MAX_BATCH_WRITE_SIZE = 25;

    private final DynamoDbTable<DynamoDbFeedInboxRecord> table;

    public DynamoDbFeedStorage(DynamoDbTable<DynamoDbFeedInboxRecord> table) {
        this.table = table;
    }

    /**
     * Zapisuje wpisy do feed inbox w sposób idempotentny.
     *
     * Idempotencja jest krytyczna, bo fan-out worker może ponowić event
     * po retry, rebalance Kafki albo częściowej awarii.
     *
     * Warunek:
     * zapisujemy rekord tylko wtedy, gdy taki PK + SK jeszcze nie istnieje.
     *
     * Jeśli rekord już istnieje, ignorujemy błąd ConditionalCheckFailedException.
     */
    @Override
    public void appendIdempotent(Collection<FeedInboxItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        /*
         * DynamoDB BatchWrite nie obsługuje condition expression,
         * więc dla idempotencji używamy pojedynczych PutItem z warunkiem.
         *
         * To jest świadomy kompromis:
         * - wolniejsze niż BatchWrite,
         * - ale bezpieczne przy retry eventów.
         *
         * Przy bardzo dużym fan-out można to zoptymalizować przez:
         * - async client,
         * - parallel writes,
         * - kolejki per shard,
         * - albo BatchWrite + osobny dedupe key.
         */
        for (FeedInboxItem item : items) {
            putOneIdempotent(item);
        }
    }

    /**
     * Usuwa post z feed inbox.
     *
     * Uwaga produkcyjna:
     * DynamoDB nie ma prostego globalnego delete po postId,
     * jeśli postId nie jest kluczem partycji.
     *
     * Dlatego ta metoda w produkcji wymaga jednego z podejść:
     *
     * 1. osobny GSI po postId,
     * 2. zapis odwrotnego indeksu postId -> userId + sortKey,
     * 3. asynchroniczne tombstone/filtering przy odczycie,
     * 4. TTL / lazy cleanup.
     *
     * Tutaj celowo rzucamy wyjątek, żeby nie udawać,
     * że globalny delete po postId jest tani w DynamoDB.
     */
    @Override
    public void removePost(UUID postId) {
        throw new UnsupportedOperationException(
                "DynamoDB removePost(postId) requires a GSI or reverse index. " +
                        "Recommended production approach: write post tombstone and filter deleted posts during hydration."
        );
    }

    /**
     * Pobiera ID postów z feed inbox użytkownika.
     *
     * Brak cursora:
     * - pobiera pierwszą stronę feedu.
     *
     * Cursor obecny:
     * - pobiera elementy starsze niż beforeCreatedAt#beforePostId.
     *
     * DynamoDB Query działa po:
     * - partition key userId,
     * - sort key createdAtPostId.
     */
    @Override
    public List<UUID> getPostIds(UUID userId, Instant beforeCreatedAt, UUID beforePostId, int limit) {
        if (userId == null) {
            return List.of();
        }

        int safeLimit = normalizeLimit(limit);

        QueryConditional condition;

        /*
         * Pierwsza strona feedu.
         *
         * Query po samej partycji userId.
         * scanIndexForward(false) poniżej sprawi, że dostaniemy najnowsze rekordy.
         */
        if (beforeCreatedAt == null || beforePostId == null) {
            condition = QueryConditional.keyEqualTo(
                    Key.builder()
                            .partitionValue(userId.toString())
                            .build()
            );
        } else {
            /*
             * Kolejna strona.
             *
             * Szukamy rekordów z sort key mniejszym niż cursor.
             *
             * Ponieważ czytamy DESC, "mniejszy sort key" oznacza starsze wpisy,
             * jeśli sort key zaczyna się od timestampu ISO.
             */
            String cursorSortKey = sortKey(beforeCreatedAt, beforePostId);

            condition = QueryConditional.sortLessThan(
                    Key.builder()
                            .partitionValue(userId.toString())
                            .sortValue(cursorSortKey)
                            .build()
            );
        }

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(condition)
                .scanIndexForward(false)
                .limit(safeLimit)
                .build();

        List<UUID> result = new ArrayList<>();

        /*
         * Query zwraca strony wyników.
         *
         * W tym przypadku limit ogranicza liczbę rekordów,
         * więc zwykle wystarczy przejść po pierwszych zwróconych itemach.
         */
        for (Page<DynamoDbFeedInboxRecord> page : table.query(request)) {
            for (DynamoDbFeedInboxRecord item : page.items()) {
                result.add(UUID.fromString(item.getPostId()));

                if (result.size() >= safeLimit) {
                    return result;
                }
            }
        }

        return result;
    }

    /**
     * Wstawia pojedynczy wpis feed inbox z warunkiem idempotencji.
     */
    private void putOneIdempotent(FeedInboxItem item) {
        DynamoDbFeedInboxRecord record = toRecord(item);

        PutItemEnhancedRequest<DynamoDbFeedInboxRecord> request =
                PutItemEnhancedRequest.builder(DynamoDbFeedInboxRecord.class)
                        .item(record)

                        /*
                         * Warunek idempotencji.
                         *
                         * Jeśli rekord o tym userId + createdAtPostId już istnieje,
                         * DynamoDB zwróci ConditionalCheckFailedException.
                         */
                        .conditionExpression(Expression.builder()
                                .expression("attribute_not_exists(userId) AND attribute_not_exists(createdAtPostId)")
                                .build())
                        .build();

        try {
            table.putItem(request);
        } catch (ConditionalCheckFailedException ignored) {
            /*
             * Ten wpis już istnieje.
             *
             * To normalne przy retry fan-outu, więc ignorujemy.
             */
        }
    }

    /**
     * Mapuje domenowy FeedInboxItem na rekord DynamoDB.
     */
    private DynamoDbFeedInboxRecord toRecord(FeedInboxItem item) {
        DynamoDbFeedInboxRecord record = new DynamoDbFeedInboxRecord();

        record.setUserId(item.userId().toString());
        record.setCreatedAtPostId(sortKey(item.createdAt(), item.postId()));
        record.setPostId(item.postId().toString());
        record.setAuthorId(item.authorId().toString());
        record.setScore(item.score());
        record.setSource(item.source());
        record.setShardId(item.shardId());
        record.setCreatedAt(item.createdAt());

        /*
         * TTL przykładowo po 90 dniach.
         *
         * Jeżeli produkt wymaga dłuższej historii feedu,
         * można to wyłączyć albo zwiększyć.
         */
        record.setExpiresAtEpochSeconds(
                item.createdAt()
                        .plus(90, ChronoUnit.DAYS)
                        .getEpochSecond()
        );

        return record;
    }

    /**
     * Buduje sort key feedu.
     *
     * Format:
     * timestamp#postId
     *
     * Timestamp ISO-8601 sortuje się leksykograficznie zgodnie z czasem,
     * o ile zawsze używamy UTC/Instant.
     */
    private String sortKey(Instant createdAt, UUID postId) {
        return createdAt.toString() + "#" + postId;
    }

    /**
     * Ogranicza limit odczytu z DynamoDB.
     *
     * DynamoDB Query jest szybkie, ale nadal nie chcemy pozwolić klientowi
     * pobrać tysięcy rekordów jednym requestem.
     */
    private int normalizeLimit(int limit) {
        if (limit < 1) {
            return 20;
        }

        return Math.min(limit, 100);
    }
}