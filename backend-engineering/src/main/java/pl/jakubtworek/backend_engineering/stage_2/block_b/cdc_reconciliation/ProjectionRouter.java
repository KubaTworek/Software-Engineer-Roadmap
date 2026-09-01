package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Atomic alias used to switch readers only after a candidate projection is verified. */
public final class ProjectionRouter {

    private final AtomicReference<OrderProjectionStore> active;

    public ProjectionRouter(OrderProjectionStore initial) {
        active = new AtomicReference<>(Objects.requireNonNull(initial));
    }

    public OrderProjectionStore active() {
        return active.get();
    }

    public void activate(OrderProjectionStore candidate) {
        active.set(Objects.requireNonNull(candidate));
    }
}
