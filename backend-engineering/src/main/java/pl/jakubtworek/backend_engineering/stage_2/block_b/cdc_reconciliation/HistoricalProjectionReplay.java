package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import java.util.List;

/** Replays into a disposable projection and never invokes live business effects. */
public final class HistoricalProjectionReplay {

    public ReplayReport replay(List<CdcRecord> history, OrderProjectionStore target) {
        ProjectionPipeline pipeline = new ProjectionPipeline(target, ProjectionPipeline.BusinessEffect.none());
        int applied = 0;
        int gaps = 0;
        int ignored = 0;
        for (CdcRecord record : history) {
            OrderProjectionStore.ApplyResult result = pipeline.process(record, ProjectionPipeline.Purpose.REBUILD);
            switch (result) {
                case APPLIED -> applied++;
                case GAP_APPLIED -> {
                    applied++;
                    gaps++;
                }
                case DUPLICATE, STALE -> ignored++;
            }
        }
        return new ReplayReport(applied, gaps, ignored);
    }

    public record ReplayReport(int applied, int gapsObserved, int ignoredDuplicateOrStale) {
    }
}
