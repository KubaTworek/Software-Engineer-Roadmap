package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OnlineProjectionBackfillTest {

    @Test
    void candidateConsumesSnapshotAndConcurrentTailBeforeAtomicCutover() {
        InMemoryCdcSource source = new InMemoryCdcSource(2);
        source.create("order-1", "NEW", 1_000);
        source.create("order-2", "NEW", 2_000);
        AtomicInteger liveEffects = new AtomicInteger();
        OrderProjectionStore live = new OrderProjectionStore();
        ProjectionPipeline livePipeline = new ProjectionPipeline(live, ignored -> liveEffects.incrementAndGet());
        source.changesAfter(0).forEach(record ->
                livePipeline.process(record, ProjectionPipeline.Purpose.LIVE));
        ProjectionRouter router = new ProjectionRouter(live);

        OnlineProjectionBackfill.Session session = new OnlineProjectionBackfill().start(source);

        // Writes continue while the candidate snapshot is being built.
        source.update("order-1", "PAID", 1_000);
        source.create("order-3", "NEW", 3_000);
        source.changesAfter(2).forEach(record ->
                livePipeline.process(record, ProjectionPipeline.Purpose.LIVE));
        assertThat(router.active()).isSameAs(live);

        session.applySnapshot();
        OnlineProjectionBackfill.CutoverResult cutover = session.catchUpVerifyAndActivate(
                source, new ProjectionReconciler(), router);

        assertThat(cutover.activated()).isTrue();
        assertThat(router.active()).isSameAs(session.candidate()).isNotSameAs(live);
        assertThat(new ProjectionReconciler().compare(source.currentRows(), router.active())).isEmpty();
        assertThat(router.active().find("order-1")).get()
                .extracting(OrderProjectionStore.ProjectedOrder::status).isEqualTo("PAID");
        assertThat(router.active().find("order-3")).isPresent();
        assertThat(liveEffects).hasValue(4);
    }

    @Test
    void cutoverIsRejectedWhenCandidateDriftsFromAuthoritativeState() {
        InMemoryCdcSource source = new InMemoryCdcSource(1);
        source.create("order-1", "NEW", 1_000);
        OrderProjectionStore current = new OrderProjectionStore();
        ProjectionRouter router = new ProjectionRouter(current);
        OnlineProjectionBackfill.Session session = new OnlineProjectionBackfill().start(source);
        session.applySnapshot();
        session.candidate().repairFromSource(new AuthoritativeOrder("order-1", "CORRUPTED", 1_000, 1));

        OnlineProjectionBackfill.CutoverResult result = session.catchUpVerifyAndActivate(
                source, new ProjectionReconciler(), router);

        assertThat(result.activated()).isFalse();
        assertThat(result.drift()).singleElement()
                .extracting(ProjectionReconciler.Drift::type)
                .isEqualTo(ProjectionReconciler.DriftType.VALUE_MISMATCH);
        assertThat(router.active()).isSameAs(current);
    }
}
