# Retry, Circuit Breaker, Bulkhead i token bucket

[Mapa przykładów](../LABORATORIA.md) · [Karty wzorców](../catalog/10-resilience/README.md)

Ten dokument zawiera rozwinięty model i scenariusze jego poprawności w blokach Markdown. Karty katalogu
pozostają punktem wejścia do intuicji, konkretnych zastosowań, kosztu i kontrprzykładów. Modele mają zakres
lokalny i in-memory; trwałość oraz granice rozproszone wymagają osobnego kontraktu.

## Model

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: ResiliencePatterns</summary>

```java
package pl.jakubtworek.patterns.systems;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Małe modele ochrony wywołań. Pełniejszą politykę czasu, backoff i jitter
 * przedstawia labs.resilience.RetryExecutor. Żaden z tych wzorców nie cofa skutku
 * operacji, która zakończyła się timeoutem po stronie klienta.
 */
public final class ResiliencePatterns {
    private ResiliencePatterns() {
    }

    // RETRY: limit prób i klasyfikacja błędów. Backoff/deadline należą do ćwiczenia rozszerzającego.
    public static <T> T retry(int maxAttempts, Predicate<RuntimeException> transientFailure, Supplier<T> call) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("at least one attempt");
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException failure) {
                if (attempt == maxAttempts || !transientFailure.test(failure)) {
                    throw failure;
                }
            }
        }
    }

    // CIRCUIT BREAKER: zegar jest monotoniczny i wstrzyknięty; model jednowątkowy, jedna próba HALF_OPEN.
    public static final class CircuitBreaker {
        public enum State {
            CLOSED, OPEN, HALF_OPEN
        }

        private final int threshold;
        private final long cooldown;
        private final LongSupplier time;
        private int failures;
        private long openedAt;
        private State state = State.CLOSED;

        public CircuitBreaker(int threshold, long cooldown, LongSupplier time) {
            if (threshold < 1 || cooldown <= 0) {
                throw new IllegalArgumentException("positive limits");
            }
            this.threshold = threshold;
            this.cooldown = cooldown;
            this.time = time;
        }

        public State state() {
            return state;
        }

        public <T> T call(Supplier<T> action) {
            if (state == State.HALF_OPEN) {
                throw new RejectedExecutionException("probe running");
            }
            if (state == State.OPEN) {
                if (time.getAsLong() - openedAt < cooldown) {
                    throw new RejectedExecutionException("circuit open");
                }
                state = State.HALF_OPEN;
            }
            try {
                T result = action.get();
                failures = 0;
                state = State.CLOSED;
                return result;
            } catch (RuntimeException failure) {
                // Nieudana próba HALF_OPEN rozpoczyna nowe okno, a nie korzysta ze starego openedAt.
                if (state == State.HALF_OPEN || ++failures >= threshold) {
                    state = State.OPEN;
                    openedAt = time.getAsLong();
                }
                throw failure;
            }
        }
    }

    // BULKHEAD: współbieżność ograniczamy bez kolejki; permit wraca w finally.
    public static final class Bulkhead {
        private final Semaphore permits;

        public Bulkhead(int capacity) {
            if (capacity < 1) {
                throw new IllegalArgumentException("positive capacity");
            }
            permits = new Semaphore(capacity);
        }

        public <T> T call(Supplier<T> action) {
            if (!permits.tryAcquire()) {
                throw new RejectedExecutionException("bulkhead full");
            }
            // Permit obejmuje cały czas pracy, również ścieżkę wyjątku. Brak finally wyczerpałby pulę.
            try {
                return action.get();
            } finally {
                permits.release();
            }
        }
    }

    // TOKEN BUCKET: dyskretne uzupełnianie co jednostkę czasu, zegar monotoniczny, stan lokalny.
    public static final class TokenBucket {
        private final long capacity;
        private final LongSupplier time;
        private long tokens;
        private long lastRefill;

        public TokenBucket(long capacity, LongSupplier time) {
            if (capacity < 1) {
                throw new IllegalArgumentException("positive capacity");
            }
            this.capacity = capacity;
            this.tokens = capacity;
            this.time = time;
            lastRefill = time.getAsLong();
        }

        public boolean acquire() {
            long now = time.getAsLong();
            long elapsed = now - lastRefill;
            if (elapsed < 0) {
                throw new IllegalStateException("time moved backwards");
            }
            // Min przed dodaniem chroni przed overflow przy długiej bezczynności.
            tokens += Math.min(capacity - tokens, elapsed);
            lastRefill = now;
            if (tokens == 0) {
                return false;
            }
            tokens--;
            return true;
        }
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: ResiliencePatternsTest</summary>

```java
package pl.jakubtworek.patterns;

import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.patterns.systems.ResiliencePatterns.*;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ResiliencePatternsTest {
    @Test
    void retryStopsAtLimitAndDoesNotRetryPermanentFailure() {
        var attempts = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> retry(3, error -> true, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException();
        }));
        assertEquals(3, attempts.get());
        attempts.set(0);
        assertThrows(IllegalArgumentException.class, () -> retry(
                3, failure -> !(failure instanceof IllegalArgumentException), () -> {
                    attempts.incrementAndGet();
                    throw new IllegalArgumentException();
                }));
        assertEquals(1, attempts.get());
    }

    @Test
    void retryReturnsFirstSuccessfulResult() {
        var attempts = new AtomicInteger();
        assertEquals("ok", retry(3, e -> true, () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException();
            }
            return "ok";
        }));
        assertEquals(2, attempts.get());
    }

    @Test
    void circuitRejectsBeforeCooldownAndRecoversWithSuccessfulProbe() {
        var time = new AtomicLong();
        var calls = new AtomicInteger();
        var breaker = new CircuitBreaker(2, 10, time::get);
        for (int i = 0; i < 2; i++) assertThrows(IllegalStateException.class, () -> breaker.call(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException();
        }));
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertThrows(RejectedExecutionException.class, () -> breaker.call(() -> {
            calls.incrementAndGet();
            return "bad";
        }));
        assertEquals(2, calls.get());
        time.set(10);
        assertEquals("ok", breaker.call(() -> {
            assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());
            return "ok";
        }));
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
    }

    @Test
    void failedHalfOpenProbeRestartsCooldown() {
        var time = new AtomicLong();
        var breaker = new CircuitBreaker(1, 10, time::get);
        assertThrows(IllegalStateException.class, () -> breaker.call(() -> {
            throw new IllegalStateException();
        }));
        time.set(10);
        assertThrows(IllegalStateException.class, () -> breaker.call(() -> {
            throw new IllegalStateException();
        }));
        time.set(19);
        assertThrows(RejectedExecutionException.class, () -> breaker.call(() -> "too early"));
        time.set(20);
        assertEquals("ok", breaker.call(() -> "ok"));
    }

    @Test
    void bulkheadRejectsExcessWorkAndReleasesPermitAfterFailure() {
        var bulkhead = new Bulkhead(1);
        assertThrows(IllegalStateException.class, () -> bulkhead.call(() -> {
            assertThrows(RejectedExecutionException.class, () -> bulkhead.call(() -> "nested"));
            throw new IllegalStateException();
        }));
        assertEquals("recovered", bulkhead.call(() -> "recovered"));
    }

    @Test
    void tokenBucketLimitsBurstAndCapsRefill() {
        var time = new AtomicLong();
        var bucket = new TokenBucket(2, time::get);
        assertTrue(bucket.acquire());
        assertTrue(bucket.acquire());
        assertFalse(bucket.acquire());
        time.set(1);
        assertTrue(bucket.acquire());
        assertFalse(bucket.acquire());
        time.set(100);
        assertTrue(bucket.acquire());
        assertTrue(bucket.acquire());
        assertFalse(bucket.acquire());
    }
}
```

</details>
