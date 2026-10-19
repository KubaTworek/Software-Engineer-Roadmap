package pl.jakubtworek.backend.common.web;

import java.time.Duration;

/**
 * Kontekst absolutnego deadline requestu. Absolutny czas, w przeciwieństwie do timeoutu
 * resetowanego na każdym hopie, zachowuje jeden end-to-end budget. ThreadLocal jest poprawny
 * dla synchronicznego servletowego flow; kod reaktywny powinien użyć Reactor Context.
 */
public final class DeadlineContext {
    public static final String HEADER = "X-Request-Deadline-Epoch-Millis";
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private DeadlineContext() { }

    static void set(long epochMillis) { DEADLINE.set(epochMillis); }
    static void clear() { DEADLINE.remove(); }
    public static String currentHeader() { return DEADLINE.get() == null ? null : DEADLINE.get().toString(); }

    public static Duration timeoutCappedAt(Duration maximum) {
        Long deadline = DEADLINE.get();
        if (deadline == null) return maximum;
        long remaining = deadline - System.currentTimeMillis() - 100; // rezerwa na cleanup i odpowiedź
        if (remaining <= 0) throw new IllegalStateException("End-to-end request deadline exceeded");
        return Duration.ofMillis(Math.min(maximum.toMillis(), remaining));
    }
}
