package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Measures elapsed time from a monotonic source such as System.nanoTime(). */
public final class MonotonicTimer {

    private final LongSupplier ticker;
    private final long startedAtNanos;

    private MonotonicTimer(LongSupplier ticker) {
        this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
        this.startedAtNanos = ticker.getAsLong();
    }

    public static MonotonicTimer start() {
        return new MonotonicTimer(System::nanoTime);
    }

    public static MonotonicTimer start(LongSupplier ticker) {
        return new MonotonicTimer(ticker);
    }

    public Duration elapsed() {
        long elapsed = ticker.getAsLong() - startedAtNanos;
        if (elapsed < 0) {
            throw new IllegalStateException("monotonic source moved backwards");
        }
        return Duration.ofNanos(elapsed);
    }
}
