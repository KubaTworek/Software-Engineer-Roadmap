package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import java.util.Objects;

/** Separates rebuilding a read model from business side effects. */
public final class ProjectionPipeline {

    private final OrderProjectionStore projection;
    private final BusinessEffect businessEffect;

    public ProjectionPipeline(OrderProjectionStore projection, BusinessEffect businessEffect) {
        this.projection = Objects.requireNonNull(projection);
        this.businessEffect = Objects.requireNonNull(businessEffect);
    }

    public OrderProjectionStore.ApplyResult process(CdcRecord record, Purpose purpose) {
        OrderProjectionStore.ApplyResult result = projection.apply(record);
        if (purpose == Purpose.LIVE
                && record.origin() == CdcRecord.Origin.STREAM
                && (result == OrderProjectionStore.ApplyResult.APPLIED
                || result == OrderProjectionStore.ApplyResult.GAP_APPLIED)) {
            businessEffect.onLiveChange(record);
        }
        return result;
    }

    public enum Purpose {
        LIVE,
        REBUILD,
        REPAIR
    }

    @FunctionalInterface
    public interface BusinessEffect {
        void onLiveChange(CdcRecord record);

        static BusinessEffect none() {
            return ignored -> { };
        }
    }
}
