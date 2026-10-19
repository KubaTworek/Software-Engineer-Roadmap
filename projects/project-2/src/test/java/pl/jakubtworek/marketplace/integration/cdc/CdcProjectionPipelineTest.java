package pl.jakubtworek.marketplace.integration.cdc;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class CdcProjectionPipelineTest {
    @Test void joinsBufferedStreamAfterSnapshotWithoutReapplyingOldChanges() {
        var pipeline = new CdcProjectionPipeline<String, String>(CdcProjectionPipeline.upsertProjector());
        pipeline.onChange(change(0, 9, "order", "old"));
        pipeline.onChange(change(0, 11, "order", "new"));

        pipeline.finishInitialSnapshot(Map.of("order", "snapshot"), Map.of(0, 10L));

        assertThat(pipeline.projection()).containsEntry("order", "new");
        assertThat(pipeline.checkpoints()).containsEntry(0, 11L);
    }

    @Test void poisonBlocksOnlyItsPartitionAndCanResumeAfterFix() {
        AtomicBoolean fixed = new AtomicBoolean();
        var pipeline = new CdcProjectionPipeline<String, String>((projection, change) -> {
            if (change.value().equals("poison") && !fixed.get()) throw new IllegalArgumentException("bad schema");
            CdcProjectionPipeline.<String, String>upsertProjector().apply(projection, change);
        });
        pipeline.finishInitialSnapshot(Map.of(), Map.of());
        pipeline.onChange(change(0, 1, "a", "poison"));
        pipeline.onChange(change(0, 2, "a", "after"));
        pipeline.onChange(change(1, 1, "b", "independent"));

        assertThat(pipeline.projection()).containsEntry("b", "independent").doesNotContainKey("a");
        assertThat(pipeline.poison(0)).isPresent();

        fixed.set(true);
        pipeline.retryPoison(0);
        assertThat(pipeline.projection()).containsEntry("a", "after");
    }

    @Test void replayAndBackfillNeverInvokeBusinessEffects() {
        var pipeline = new CdcProjectionPipeline<String, String>(CdcProjectionPipeline.upsertProjector());
        pipeline.finishInitialSnapshot(Map.of(), Map.of());
        pipeline.onChange(change(0, 1, "order", "PAID"));
        pipeline.onChange(change(0, 1, "order", "PAID")); // duplicate offset
        pipeline.backfill(Map.of("missing-order", "CONFIRMED"));
        assertThat(pipeline.projection()).containsOnlyKeys("order", "missing-order");
        assertThat(pipeline.checkpoints()).containsEntry(0, 1L);
    }

    private static CdcProjectionPipeline.Change<String, String> change(int partition, long offset, String key, String value) {
        return new CdcProjectionPipeline.Change<>(new CdcProjectionPipeline.Position(partition, offset), key, value, false);
    }
}
