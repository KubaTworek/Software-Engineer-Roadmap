package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import java.util.List;

/** Snapshot plus catch-up into a new generation while the old projection remains readable. */
public final class OnlineProjectionBackfill {

    public Session start(InMemoryCdcSource source) {
        return new Session(source.beginSnapshot(), source.partitions());
    }

    public static final class Session {
        private final CdcSnapshot snapshot;
        private final int partitions;
        private final OrderProjectionStore candidate = new OrderProjectionStore();
        private final ProjectionPipeline pipeline = new ProjectionPipeline(
                candidate, ProjectionPipeline.BusinessEffect.none());
        private long consumedThrough;
        private boolean snapshotApplied;

        private Session(CdcSnapshot snapshot, int partitions) {
            this.snapshot = snapshot;
            this.partitions = partitions;
            this.consumedThrough = snapshot.highWatermark();
        }

        public void applySnapshot() {
            if (snapshotApplied) {
                return;
            }
            snapshot.records(partitions).forEach(record ->
                    pipeline.process(record, ProjectionPipeline.Purpose.REBUILD));
            snapshotApplied = true;
        }

        public CutoverResult catchUpVerifyAndActivate(
                InMemoryCdcSource source,
                ProjectionReconciler reconciler,
                ProjectionRouter router
        ) {
            if (!snapshotApplied) {
                throw new IllegalStateException("snapshot must be applied before catch-up");
            }

            // In a real connector this loop follows WAL until lag reaches the cutover threshold.
            while (consumedThrough < source.currentPosition()) {
                List<CdcRecord> tail = source.changesAfter(consumedThrough);
                tail.forEach(record -> pipeline.process(record, ProjectionPipeline.Purpose.REBUILD));
                consumedThrough = tail.getLast().sourcePosition();
            }

            CdcSnapshot verificationView = source.beginSnapshot();
            if (verificationView.highWatermark() != consumedThrough) {
                return new CutoverResult(false, consumedThrough, List.of());
            }
            List<ProjectionReconciler.Drift> drift = reconciler.compare(
                    verificationView.rows(), candidate);
            if (!drift.isEmpty()) {
                return new CutoverResult(false, consumedThrough, drift);
            }
            router.activate(candidate);
            return new CutoverResult(true, consumedThrough, List.of());
        }

        public OrderProjectionStore candidate() {
            return candidate;
        }
    }

    public record CutoverResult(
            boolean activated,
            long consumedThrough,
            List<ProjectionReconciler.Drift> drift
    ) {
        public CutoverResult {
            drift = List.copyOf(drift);
        }
    }
}
