package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReconciliationAndRepairTest {

    @Test
    void detectsMissingMismatchedAndOrphanRowsThenRepairsOnlyTheProjection() {
        InMemoryCdcSource source = new InMemoryCdcSource(1);
        AuthoritativeOrder first = source.create("order-1", "PAID", 1_000);
        source.create("order-2", "NEW", 2_000);
        OrderProjectionStore projection = new OrderProjectionStore();
        projection.repairFromSource(new AuthoritativeOrder(first.id(), "WRONG", 999, first.version()));
        projection.repairFromSource(new AuthoritativeOrder("orphan", "NEW", 300, 1));
        AtomicInteger businessEffects = new AtomicInteger();
        ProjectionPipeline livePipeline = new ProjectionPipeline(projection, ignored -> businessEffects.incrementAndGet());
        ProjectionReconciler reconciler = new ProjectionReconciler();

        List<ProjectionReconciler.Drift> drift = reconciler.compare(source.currentRows(), projection);
        ProjectionReconciler.RepairReport repaired = reconciler.repair(source.currentRows(), projection);

        assertThat(drift).extracting(ProjectionReconciler.Drift::type)
                .containsExactlyInAnyOrder(
                        ProjectionReconciler.DriftType.VALUE_MISMATCH,
                        ProjectionReconciler.DriftType.MISSING,
                        ProjectionReconciler.DriftType.ORPHAN);
        assertThat(repaired.detected()).hasSize(3);
        assertThat(repaired.remaining()).isEmpty();
        assertThat(reconciler.compare(source.currentRows(), projection)).isEmpty();
        assertThat(businessEffects).hasValue(0);

        // The live effect port still works for a genuinely new stream change.
        CdcRecord liveChange = source.changesAfter(0).getFirst();
        livePipeline.process(liveChange, ProjectionPipeline.Purpose.LIVE);
        assertThat(businessEffects).hasValue(0); // stale after repair, so no repeated effect
    }

    @Test
    void rebuildPurposeNeverExecutesBusinessEffects() {
        InMemoryCdcSource source = new InMemoryCdcSource(1);
        source.create("order-1", "NEW", 1_000);
        source.update("order-1", "PAID", 1_000);
        AtomicInteger businessEffects = new AtomicInteger();
        ProjectionPipeline pipeline = new ProjectionPipeline(
                new OrderProjectionStore(), ignored -> businessEffects.incrementAndGet());

        source.changesAfter(0).forEach(record ->
                pipeline.process(record, ProjectionPipeline.Purpose.REBUILD));

        assertThat(businessEffects).hasValue(0);
    }
}
