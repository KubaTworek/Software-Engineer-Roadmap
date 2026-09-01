package pl.jakubtworek.backend_engineering.stage_2.block_a.grpc;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Deadline uses monotonic time; wall-clock corrections cannot extend a request budget. */
public final class RpcDeadline {

    private final long deadlineNanos;
    private final LongSupplier nanoTime;

    private RpcDeadline(long deadlineNanos, LongSupplier nanoTime) {
        this.deadlineNanos = deadlineNanos;
        this.nanoTime = nanoTime;
    }

    public static RpcDeadline after(Duration duration, LongSupplier nanoTime) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("deadline duration must be positive");
        }
        long now = nanoTime.getAsLong();
        return new RpcDeadline(saturatingAdd(now, duration.toNanos()), nanoTime);
    }

    public Duration remaining() {
        return Duration.ofNanos(Math.max(0, deadlineNanos - nanoTime.getAsLong()));
    }

    public boolean expired() {
        return remaining().isZero();
    }

    public RpcDeadline child(Duration maximumBudget) {
        if (maximumBudget.isNegative() || maximumBudget.isZero()) {
            throw new IllegalArgumentException("child budget must be positive");
        }
        long childDeadline = saturatingAdd(nanoTime.getAsLong(), maximumBudget.toNanos());
        return new RpcDeadline(Math.min(deadlineNanos, childDeadline), nanoTime);
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
