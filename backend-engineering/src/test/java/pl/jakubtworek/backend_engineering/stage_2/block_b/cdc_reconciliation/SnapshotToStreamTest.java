package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotToStreamTest {

    @Test
    void changesCommittedAfterSnapshotWatermarkAreConsumedFromTheStream() {
        InMemoryCdcSource source = new InMemoryCdcSource(2);
        source.create("order-1", "NEW", 1_000);
        source.create("order-2", "NEW", 2_000);
        CdcSnapshot snapshot = source.beginSnapshot();

        source.update("order-1", "PAID", 1_000);
        source.create("order-3", "NEW", 3_000);

        OrderProjectionStore projection = new OrderProjectionStore();
        ProjectionPipeline pipeline = new ProjectionPipeline(projection, ProjectionPipeline.BusinessEffect.none());
        snapshot.records(source.partitions()).forEach(record ->
                pipeline.process(record, ProjectionPipeline.Purpose.REBUILD));
        source.changesAfter(snapshot.highWatermark()).forEach(record ->
                pipeline.process(record, ProjectionPipeline.Purpose.REBUILD));

        assertThat(snapshot.highWatermark()).isEqualTo(2);
        assertThat(projection.all()).extracting(
                        OrderProjectionStore.ProjectedOrder::id,
                        OrderProjectionStore.ProjectedOrder::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("order-1", "PAID"),
                        org.assertj.core.groups.Tuple.tuple("order-2", "NEW"),
                        org.assertj.core.groups.Tuple.tuple("order-3", "NEW"));
    }

    @Test
    void fullAfterImageAndSourceVersionSurviveDuplicateAndReorderedRebuildInput() {
        InMemoryCdcSource source = new InMemoryCdcSource(1);
        source.create("order-1", "NEW", 1_000);
        source.update("order-1", "PAID", 1_000);
        source.update("order-1", "SHIPPED", 1_000);
        List<CdcRecord> history = source.changesAfter(0);
        List<CdcRecord> reordered = new ArrayList<>(List.of(
                history.get(2), history.get(0), history.get(1), history.get(2)));
        OrderProjectionStore projection = new OrderProjectionStore();

        HistoricalProjectionReplay.ReplayReport report =
                new HistoricalProjectionReplay().replay(reordered, projection);

        assertThat(projection.find("order-1")).get()
                .extracting(OrderProjectionStore.ProjectedOrder::status,
                        OrderProjectionStore.ProjectedOrder::sourceVersion)
                .containsExactly("SHIPPED", 3L);
        assertThat(report.applied()).isEqualTo(1);
        assertThat(report.gapsObserved()).isEqualTo(1);
        assertThat(report.ignoredDuplicateOrStale()).isEqualTo(3);
    }

    @Test
    void snapshotArrivingAfterANewerStreamRecordCannotOverwriteIt() {
        InMemoryCdcSource source = new InMemoryCdcSource(1);
        source.create("order-1", "NEW", 1_000);
        CdcSnapshot snapshot = source.beginSnapshot();
        source.update("order-1", "PAID", 1_000);
        CdcRecord newer = source.changesAfter(snapshot.highWatermark()).getFirst();
        OrderProjectionStore projection = new OrderProjectionStore();

        assertThat(projection.apply(newer)).isEqualTo(OrderProjectionStore.ApplyResult.GAP_APPLIED);
        assertThat(projection.apply(snapshot.records(1).getFirst()))
                .isEqualTo(OrderProjectionStore.ApplyResult.STALE);

        assertThat(projection.find("order-1")).get()
                .extracting(OrderProjectionStore.ProjectedOrder::status).isEqualTo("PAID");
    }

    @Test
    void tombstoneVersionPreventsAStaleUpdateFromResurrectingDeletedRow() {
        InMemoryCdcSource source = new InMemoryCdcSource(1);
        source.create("order-1", "NEW", 1_000);
        source.update("order-1", "PAID", 1_000);
        source.delete("order-1");
        List<CdcRecord> history = source.changesAfter(0);
        OrderProjectionStore projection = new OrderProjectionStore();
        history.forEach(projection::apply);
        CdcRecord oldUpdate = history.get(1);
        CdcRecord redeliveredWithAnotherEnvelopeId = new CdcRecord(
                "replayed-old-update", oldUpdate.partition(), 99, oldUpdate.key(),
                oldUpdate.operation(), oldUpdate.before(), oldUpdate.after(),
                oldUpdate.sourceVersion(), oldUpdate.origin());

        assertThat(projection.find("order-1")).isEmpty();
        assertThat(projection.highestVersion("order-1")).isEqualTo(3);
        assertThat(projection.apply(redeliveredWithAnotherEnvelopeId))
                .isEqualTo(OrderProjectionStore.ApplyResult.STALE);
        assertThat(projection.find("order-1")).isEmpty();
    }
}
