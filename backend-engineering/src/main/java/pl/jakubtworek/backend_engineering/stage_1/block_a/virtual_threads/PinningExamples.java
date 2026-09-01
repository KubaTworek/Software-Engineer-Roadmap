package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

import java.util.Objects;

/**
 * On Java 21, a virtual thread that blocks while holding an intrinsic monitor
 * pins its carrier. Keep blocking I/O outside synchronized sections. This is a
 * diagnostic example, not a replacement for measuring jdk.VirtualThreadPinned.
 */
public final class PinningExamples {

    private final Object monitor = new Object();
    private String state = "initial";

    public <T> T blockingWhileHoldingMonitor(InterruptibleOperation<T> operation)
            throws InterruptedException {
        Objects.requireNonNull(operation, "operation must not be null");
        synchronized (monitor) {
            return operation.execute();
        }
    }

    public <T> T updateStateThenBlockOutsideMonitor(InterruptibleOperation<T> operation)
            throws InterruptedException {
        Objects.requireNonNull(operation, "operation must not be null");
        synchronized (monitor) {
            state = "calling";
        }
        try {
            // The potentially blocking call no longer holds the intrinsic monitor.
            return operation.execute();
        } finally {
            synchronized (monitor) {
                state = "completed";
            }
        }
    }

    public boolean currentThreadHoldsMonitor() {
        return Thread.holdsLock(monitor);
    }

    public String state() {
        synchronized (monitor) {
            return state;
        }
    }
}
