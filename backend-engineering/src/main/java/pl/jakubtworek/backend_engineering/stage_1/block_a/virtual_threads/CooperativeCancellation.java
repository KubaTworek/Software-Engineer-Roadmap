package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

import java.util.Objects;
import java.util.function.Consumer;

/** Cancellation remains cooperative for virtual threads: cancel(true) requests interruption. */
public final class CooperativeCancellation {

    public CancellationState run(
            InterruptibleOperation<?> operation,
            Consumer<Boolean> interruptFlagObserver
    ) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(interruptFlagObserver, "interruptFlagObserver must not be null");
        try {
            operation.execute();
            return CancellationState.COMPLETED;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            interruptFlagObserver.accept(Thread.currentThread().isInterrupted());
            return CancellationState.INTERRUPTED;
        }
    }
}
