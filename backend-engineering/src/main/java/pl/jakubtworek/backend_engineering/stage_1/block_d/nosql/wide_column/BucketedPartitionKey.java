package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.wide_column;

import java.time.Duration;
import java.time.Instant;

/** Tworzy ograniczone czasowo i opcjonalnie shardowane klucze partycji. */
public final class BucketedPartitionKey {

    private BucketedPartitionKey() {
    }

    public static PartitionKey create(
            String ownerId,
            Instant occurredAt,
            Duration bucketSize,
            int shards,
            String distributionKey
    ) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        if (bucketSize == null || bucketSize.isZero() || bucketSize.isNegative()) {
            throw new IllegalArgumentException("bucketSize must be positive");
        }
        if (bucketSize.getNano() != 0) {
            throw new IllegalArgumentException("bucketSize must use whole seconds");
        }
        if (shards <= 0) {
            throw new IllegalArgumentException("shards must be positive");
        }
        if (distributionKey == null || distributionKey.isBlank()) {
            throw new IllegalArgumentException("distributionKey must not be blank");
        }

        long bucketSeconds = bucketSize.getSeconds();
        long bucketStartSeconds = Math.floorDiv(occurredAt.getEpochSecond(), bucketSeconds) * bucketSeconds;
        Instant bucketStart = Instant.ofEpochSecond(bucketStartSeconds);
        int shard = Math.floorMod(distributionKey.hashCode(), shards);
        return new PartitionKey(ownerId + "#" + bucketStartSeconds + "#" + shard, bucketStart, shard);
    }

    public record PartitionKey(String value, Instant bucketStart, int shard) {
    }
}
