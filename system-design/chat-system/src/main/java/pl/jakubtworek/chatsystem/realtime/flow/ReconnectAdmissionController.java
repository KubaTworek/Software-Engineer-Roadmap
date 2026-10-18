package pl.jakubtworek.chatsystem.realtime.flow;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Bulkhead dla kosztownego catch-up po reconnect. Limit jest na pracę z bazą,
 * nie na liczbę socketów; klient bez permitu powinien ponowić z jitterem.
 */
public final class ReconnectAdmissionController {
    private final Semaphore permits;
    public ReconnectAdmissionController(int maxConcurrentCatchUps) {
        permits = new Semaphore(maxConcurrentCatchUps, true);
    }
    public Optional<Lease> tryAcquire() {
        return permits.tryAcquire() ? Optional.of(new Lease(permits)) : Optional.empty();
    }
    public Duration recommendedRetryDelay(long clientAttempt, long jitterSeed) {
        long capped = Math.min(10_000, 100L << Math.min(clientAttempt, 6));
        long jitter = Math.floorMod(jitterSeed, Math.max(1, capped / 2));
        return Duration.ofMillis(capped / 2 + jitter);
    }
    public static final class Lease implements AutoCloseable {
        private final Semaphore permits; private boolean closed;
        private Lease(Semaphore permits) { this.permits = permits; }
        @Override public void close() { if (!closed) { closed = true; permits.release(); } }
    }
}
