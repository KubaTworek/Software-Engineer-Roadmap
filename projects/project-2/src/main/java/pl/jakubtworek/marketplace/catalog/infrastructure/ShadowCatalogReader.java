package pl.jakubtworek.marketplace.catalog.infrastructure;

import java.time.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.*;

/** Strangler granicy odczytu: osobny, ograniczony bulkhead i deadline kandydata. */
public final class ShadowCatalogReader<K,V> implements AutoCloseable {
    private final Function<K,V> legacy, candidate;
    private final BiConsumer<K,Boolean> observation;
    private final ThreadPoolExecutor executor;
    private final Duration timeout, evidenceWindow;
    private final Clock clock;
    private boolean promoted;
    private long compared, mismatches, epoch;
    private Instant windowStarted;

    public ShadowCatalogReader(Function<K,V> legacy, Function<K,V> candidate, BiConsumer<K,Boolean> observation) {
        this(legacy, candidate, observation, Duration.ofMillis(25), 2, Duration.ofMinutes(5), Clock.systemUTC());
    }
    public ShadowCatalogReader(Function<K,V> legacy, Function<K,V> candidate, BiConsumer<K,Boolean> observation,
                               Duration timeout, int concurrency, Duration evidenceWindow, Clock clock) {
        this.legacy = Objects.requireNonNull(legacy);
        this.candidate = Objects.requireNonNull(candidate);
        this.observation = Objects.requireNonNull(observation);
        if (timeout.isNegative() || timeout.isZero() || concurrency < 1 || evidenceWindow.isNegative() || evidenceWindow.isZero())
            throw new IllegalArgumentException("Dodatni deadline, bulkhead i okno dowodów");
        this.timeout = timeout; this.evidenceWindow = evidenceWindow; this.clock = clock;
        this.windowStarted = clock.instant();
        this.executor = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), task -> { var thread = new Thread(task, "catalog-shadow"); thread.setDaemon(true); return thread; },
                new ThreadPoolExecutor.AbortPolicy());
    }
    public V read(K key) {
        long generation;
        boolean useCandidate;
        synchronized (this) { expireEvidence(); generation = epoch; useCandidate = promoted; }
        V old = useCandidate ? null : legacy.apply(key);
        Future<V> future = null;
        boolean equal = false;
        try {
            future = executor.submit(() -> candidate.apply(key));
            V result = future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (useCandidate) return result;
            equal = Objects.equals(old, result);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RejectedExecutionException unavailable) {
            // Anulowanie jest kooperatywne. Ignorujący interrupt adapter zajmie slot,
            // lecz brak kolejki uniemożliwia narastanie pracy i blokowanie kolejnych klientów.
        } finally { if (future != null) future.cancel(true); }
        if (useCandidate) {
            synchronized (this) { if (epoch == generation) resetEvidence(); }
            return legacy.apply(key);
        }
        synchronized (this) {
            expireEvidence();
            if (generation == epoch) { compared++; if (!equal) mismatches++; }
        }
        boolean matched = equal;
        try { executor.execute(() -> {
            try { observation.accept(key, matched); }
            catch (RuntimeException failure) {
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Shadow telemetry unavailable");
            }
        }); } catch (RejectedExecutionException saturated) { /* Statystyki lokalne są zachowane; telemetria jest best effort. */ }
        return old;
    }
    public synchronized void promote(long lag, long minSample) {
        expireEvidence();
        if (minSample <= 0 || compared < minSample || mismatches != 0 || lag != 0)
            throw new IllegalStateException("Brak świeżego dowodu zgodności read modelu");
        promoted = true;
    }
    /** Wywołaj również po zmianie wersji kandydata lub backfillu. Stare wyniki nie są dowodem nowej wersji. */
    public synchronized void rollbackReads() { resetEvidence(); }
    private void expireEvidence() {
        if (!clock.instant().isBefore(windowStarted.plus(evidenceWindow))) resetEvidence();
    }
    private void resetEvidence() {
        promoted = false; compared = 0; mismatches = 0; epoch++; windowStarted = clock.instant();
    }
    public void close() { executor.shutdownNow(); }
}
