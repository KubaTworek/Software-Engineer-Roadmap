package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Absolute request deadline propagated between services.
 *
 * <p>An absolute instant prevents every hop from starting a fresh relative
 * timeout. A child deadline may only shorten its parent and can reserve time
 * for response serialization or another downstream call.</p>
 */
public final class RequestDeadline {

    public static final String HEADER = "X-Request-Deadline-Epoch-Millis";

    private final Instant expiresAt;
    private final Clock clock;

    private RequestDeadline(Instant expiresAt, Clock clock) {
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static RequestDeadline after(Duration budget, Clock clock) {
        requirePositive(budget, "budget");
        Objects.requireNonNull(clock, "clock");
        return new RequestDeadline(clock.instant().plus(budget), clock);
    }

    public static RequestDeadline fromHeader(String headerValue, Clock clock) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new IllegalArgumentException("deadline header is required");
        }
        try {
            return new RequestDeadline(Instant.ofEpochMilli(Long.parseLong(headerValue)), clock);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("deadline header must contain epoch millis", exception);
        }
    }

    public RequestDeadline child(Duration maximumChildBudget, Duration parentReserve) {
        requirePositive(maximumChildBudget, "maximumChildBudget");
        requireNonNegative(parentReserve, "parentReserve");

        Instant now = clock.instant();
        Instant latestAllowed = expiresAt.minus(parentReserve);
        Instant childExpiry = min(latestAllowed, now.plus(maximumChildBudget));
        if (!childExpiry.isAfter(now)) {
            throw new DeadlineExceededException("no request budget remains after parent reserve");
        }
        return new RequestDeadline(childExpiry, clock);
    }

    public Duration remaining() {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public void throwIfExpired() {
        if (remaining().isZero()) {
            throw new DeadlineExceededException("request deadline has expired");
        }
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String toHeaderValue() {
        return Long.toString(expiresAt.toEpochMilli());
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(Duration duration, String name) {
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
