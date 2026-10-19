package pl.jakubtworek.backend_engineering.stage_2.block_b.websocket;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Monotonic heartbeat deadline avoids wall-clock jumps when detecting half-open sessions. */
public final class HeartbeatDeadline {

    private final long timeoutNanos;
    private final LongSupplier nanoTime;
    private long lastSignalNanos;

    public HeartbeatDeadline(Duration timeout, LongSupplier nanoTime) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        this.timeoutNanos = timeout.toNanos();
        this.nanoTime = nanoTime;
        this.lastSignalNanos = nanoTime.getAsLong();
    }

    public void signalReceived() {
        lastSignalNanos = nanoTime.getAsLong();
    }

    public boolean expired() {
        return nanoTime.getAsLong() - lastSignalNanos >= timeoutNanos;
    }
}
