package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.wide_column;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartitioningTest {

    @Test
    void shouldBoundAPartitionByTimeAndDistributeWritesAcrossShards() {
        Instant time = Instant.parse("2026-08-31T10:15:30Z");

        var first = BucketedPartitionKey.create("device-1", time, Duration.ofHours(1), 4, "event-1");
        var second = BucketedPartitionKey.create("device-1", time, Duration.ofHours(1), 4, "event-2");

        assertThat(first.bucketStart()).isEqualTo(Instant.parse("2026-08-31T10:00:00Z"));
        assertThat(first.shard()).isNotEqualTo(second.shard());
        assertThat(first.value()).startsWith("device-1#");
    }

    @Test
    void shouldMoveNewWritesToTheNextTimeBucket() {
        var before = BucketedPartitionKey.create(
                "device-1", Instant.parse("2026-08-31T10:59:59Z"), Duration.ofHours(1), 1, "event"
        );
        var after = BucketedPartitionKey.create(
                "device-1", Instant.parse("2026-08-31T11:00:00Z"), Duration.ofHours(1), 1, "event"
        );

        assertThat(before.value()).isNotEqualTo(after.value());
    }

    @Test
    void shouldExposeAHotPartitionAndTheEffectOfSharding() {
        List<String> unsharded = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            unsharded.add("tenant-popular");
        }
        for (int i = 0; i < 10; i++) {
            unsharded.add("tenant-" + i);
        }

        PartitionLoadAnalyzer.LoadReport report = PartitionLoadAnalyzer.analyze(unsharded);

        List<String> sharded = new ArrayList<>();
        Instant time = Instant.parse("2026-08-31T10:00:00Z");
        for (int i = 0; i < 100; i++) {
            sharded.add(BucketedPartitionKey.create(
                    "tenant-popular",
                    time,
                    Duration.ofHours(1),
                    4,
                    "event-" + i
            ).value());
        }
        PartitionLoadAnalyzer.LoadReport shardedReport = PartitionLoadAnalyzer.analyze(sharded);

        assertThat(report.busiestPartition()).isEqualTo("tenant-popular");
        assertThat(report.busiestShare()).isEqualTo(0.9);
        assertThat(report.hasHotPartition(0.5)).isTrue();
        assertThat(shardedReport.hasHotPartition(0.5)).isFalse();
        assertThat(shardedReport.busiestShare()).isLessThan(report.busiestShare());
    }
}
