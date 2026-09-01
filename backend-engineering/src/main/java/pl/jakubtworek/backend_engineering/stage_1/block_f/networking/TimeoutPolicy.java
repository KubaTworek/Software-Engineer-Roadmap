package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import java.time.Duration;

/** Separates obtaining a connection, one socket read and the whole request deadline. */
public record TimeoutPolicy(Duration connect, Duration read, Duration request) {

    public TimeoutPolicy {
        requirePositive(connect, "connect");
        requirePositive(read, "read");
        requirePositive(request, "request");
        if (request.compareTo(connect) <= 0) {
            throw new IllegalArgumentException("request timeout must leave time after connect");
        }
    }

    public Duration remainingAfterConnect(Duration elapsed) {
        if (elapsed.isNegative()) throw new IllegalArgumentException("elapsed cannot be negative");
        Duration remaining = request.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public Duration effectiveReadTimeout(Duration elapsed) {
        Duration remaining = remainingAfterConnect(elapsed);
        return remaining.compareTo(read) < 0 ? remaining : read;
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " timeout must be positive");
        }
    }
}
