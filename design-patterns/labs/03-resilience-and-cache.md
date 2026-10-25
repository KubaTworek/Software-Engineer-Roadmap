# Jeden budżet retry i ograniczona kopia danych

[Lista laboratoriów](README.md) · [Odporność](../catalog/10-resilience/README.md) ·
[Cache](../catalog/07-persistence/README.md)

**Modele w tym dokumencie:** [RetryExecutor](03-resilience-and-cache.md#model-retryexecutor),
[BoundedTtlCache](03-resilience-and-cache.md#model-boundedttlcache). **Scenariusze poprawności:**
[dane, operacje i oczekiwane wyniki](03-resilience-and-cache.md#scenariusze-poprawności).

## Retry zaczyna się od prawa do ponowienia

Liczba prób obejmuje pierwsze wywołanie. Klasyfikator odróżnia awarię, która może zniknąć, od błędu trwałego.
Nadal to klient polityki odpowiada za idempotencję: timeout obciążenia karty nie daje automatycznie prawa do
nowej płatności z nowym identyfikatorem. RetryExecutor nie zna semantyki payloadu.

Wstrzyknięte są trzy zależności: monotoniczny zegar, operacja oczekiwania i funkcja jitter. Dzięki temu test
nie śpi ani nie losuje. Wariant sieciowy mógłby podać System.nanoTime i implementację oczekiwania reagującą na
przerwanie, a test przesuwa AtomicLong o dokładną liczbę nanosekund.

## Ślad wykonania

Dla initialBackoff=10, maxBackoff=25 i budżetu 100, przy jitter zwracającym górną granicę, cztery próby
rozdzielają opóźnienia 10, 20 i 25. Koszt samych wywołań również jest odejmowany od budżetu. Funkcja
produkcyjnego full jitter powinna wybierać wartość z przedziału od 0 do aktualnej granicy; samo
`bound -> bound` w teście jest deterministycznym wyborem skrajnym, nie losowaniem.

| Sytuacja | Zachowanie |
| --- | --- |
| Błąd nieponawialny | natychmiastowy wyjątek bez snu |
| Ostatnia próba zawodzi | ostatni wyjątek bez kolejnej próby |
| Zostały 3 ns, planowany delay to 5 ns | BudgetExceeded bez bezcelowego snu |
| Scheduler obudził po deadline | brak następnego wywołania |
| Jitter zwraca -1 lub więcej niż bound | błąd konfiguracji polityki |

Wykorzystujemy różnicę monotonicznych odczytów, zamiast dodawać deadline do dowolnie dużej wartości zegara.
Backoff jest nasycany przed mnożeniem, więc konfiguracja bliska zakresowi long nie przewija się do ujemnej
liczby.

**Granica:** polityka sprawdza budżet przed kolejnymi próbami i oczekiwaniem. Nie potrafi siłą zatrzymać
blokującego `call.get()`, a poprawny wynik rozpoczętej próby może wrócić już po terminie. Sam klient I/O musi
respektować przekazany pozostały czas i anulowanie. Wykonanie jest synchroniczne, nie jest to scheduler ani
gotowy klient HTTP z timerem wywłaszczającym zadanie.

## Cache: niezależne decyzje o czasie i pamięci

BoundedTtlCache używa LinkedHashMap w kolejności dostępu. TTL wyznacza przydatność wpisu, LRU wybór ofiary po
przekroczeniu capacity. Te polityki nie są równoważne: często odczytywany wpis nadal może wygasnąć, a świeży,
rzadki wpis może zostać usunięty przez limit liczby elementów.

Loader zwraca Optional, więc brak produktu jest prawidłowym zapamiętywanym wynikiem, a nie wyjątkiem źródła.
Dzięki negative caching dwa odczyty brakującego klucza wykonują jeden load. Po pojawieniu się produktu trzeba
poczekać na TTL albo jawnie usunąć negatywną kopię. Inaczej cache opóźni widoczność nowego rekordu.

Odczyt na granicy `age == ttl` powoduje ponowne ładowanie. TTL liczy się od zakończenia load. Ręczny refresh
najpierw uzyskuje nowy wynik, więc jego błąd nie niszczy jeszcze świeżej starej kopii. Gdy get wykrył już
wygaśnięcie, stary wpis jest usuwany i błąd loadera nie daje stale fallback automatycznie.

Test LRU wykonuje a, b, a, c: ofiarą jest b. Kolejny b znów ładuje źródło. Oczekiwany koszt operacji mapy jest
O(1), pamięć O(capacity), nie licząc rozmiaru wartości. Wygasłe wpisy są sprzątane przy dostępie lub
wypychaniu, więc size może obejmować wpisy, które nie zostały jeszcze ponownie sprawdzone.

**Granica:** cache ma jednego właściciela wątku, nie kopiuje mutowalnego V, nie zapewnia atomowości z bazą i
nie deduplikuje współbieżnych missów. Osobny [SingleFlight](05-concurrency.md) pokazuje ostatni mechanizm; ich
połączenie wymagałoby jednego zgodnego kontraktu czasu i własności.

## Model RetryExecutor

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: RetryExecutor</summary>

```java
package pl.jakubtworek.patterns.labs.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Limit prób + wspólny budżet + exponential backoff + wstrzyknięty full jitter.
 */
public final class RetryExecutor {
    private final int maxAttempts;
    private final long initialBackoff;
    private final long maxBackoff;
    private final LongSupplier nanoTime;
    private final LongConsumer sleepNanos;
    private final LongUnaryOperator jitter;

    public RetryExecutor(
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            LongSupplier nanoTime,
            LongConsumer sleepNanos,
            LongUnaryOperator jitter) {
        this.initialBackoff = initialBackoff.toNanos();
        this.maxBackoff = maxBackoff.toNanos();
        if (maxAttempts < 1 || this.initialBackoff <= 0 || this.maxBackoff < this.initialBackoff) {
            throw new IllegalArgumentException("Invalid retry limits");
        }
        this.maxAttempts = maxAttempts;
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.sleepNanos = Objects.requireNonNull(sleepNanos);
        this.jitter = Objects.requireNonNull(jitter);
    }

    public static final class BudgetExceeded extends RuntimeException {
        public BudgetExceeded(RuntimeException lastFailure) {
            super("Retry budget exhausted", lastFailure);
        }
    }

    public <T> T execute(Duration budget, Predicate<RuntimeException> retryable, Supplier<T> call) {
        long budgetNanos = budget.toNanos();
        if (budgetNanos <= 0) {
            throw new IllegalArgumentException("Positive time budget required");
        }
        long started = nanoTime.getAsLong();
        long backoff = initialBackoff;
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            requireRemaining(started, budgetNanos, lastFailure);
            try {
                return call.get();
            } catch (RuntimeException failure) {
                if (attempt == maxAttempts || !retryable.test(failure)) {
                    throw failure;
                }
                lastFailure = failure;
            }
            long remaining = requireRemaining(started, budgetNanos, lastFailure);
            long delay = jitter.applyAsLong(backoff);
            if (delay < 0 || delay > backoff) {
                throw new IllegalArgumentException("Jitter must be between zero and backoff");
            }
            if (delay >= remaining) {
                // Nie śpimy do deadline tylko po to, by natychmiast odrzucić kolejną próbę.
                throw new BudgetExceeded(lastFailure);
            }
            sleepNanos.accept(delay);
            // Nasycenie przed mnożeniem chroni przed overflow dla dużych konfiguracji.
            backoff = backoff >= maxBackoff - backoff ? maxBackoff : backoff * 2;
        }
        throw new AssertionError("Attempt loop must return or throw");
    }

    private long requireRemaining(long started, long budget, RuntimeException lastFailure) {
        long elapsed = nanoTime.getAsLong() - started;
        if (elapsed < 0) {
            throw new IllegalStateException("Monotonic clock moved backwards");
        }
        if (elapsed >= budget) {
            throw new BudgetExceeded(lastFailure);
        }
        return budget - elapsed;
    }
}
```

</details>

## Model BoundedTtlCache

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: BoundedTtlCache</summary>

```java
package pl.jakubtworek.patterns.labs.cache;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Cache-aside + TTL + LRU + negative caching; celowo jeden właściciel stanu.
 */
public final class BoundedTtlCache<K, V> {
    private record Entry<V>(Optional<V> value, long loadedAt) {
    }

    private final int capacity;
    private final long ttlNanos;
    private final LongSupplier nanoTime;
    private final Function<K, Optional<V>> loader;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

    public BoundedTtlCache(int capacity, Duration ttl, LongSupplier nanoTime, Function<K, Optional<V>> loader) {
        ttlNanos = ttl.toNanos();
        if (capacity < 1 || ttlNanos <= 0) {
            throw new IllegalArgumentException("Positive capacity and TTL required");
        }
        this.capacity = capacity;
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.loader = Objects.requireNonNull(loader);
    }

    public Optional<V> get(K key) {
        Objects.requireNonNull(key);
        Entry<V> entry = entries.get(key);
        if (entry != null) {
            long age = nanoTime.getAsLong() - entry.loadedAt();
            if (age < 0) {
                throw new IllegalStateException("Monotonic clock moved backwards");
            }
            if (age < ttlNanos) {
                return entry.value();
            }
            entries.remove(key);
        }
        return refresh(key);
    }

    public Optional<V> refresh(K key) {
        // Loader może zawieść. Stara kopia nie jest wcześniej nadpisywana pustą wartością.
        Optional<V> loaded = Objects.requireNonNull(loader.apply(Objects.requireNonNull(key)));
        entries.put(key, new Entry<>(loaded, nanoTime.getAsLong()));
        while (entries.size() > capacity) {
            K eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
        }
        return loaded;
    }

    public void invalidate(K key) {
        entries.remove(key);
    }

    public int size() {
        return entries.size();
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: ResilienceAndCacheLabTest</summary>

```java
package pl.jakubtworek.patterns.labs;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.patterns.labs.cache.BoundedTtlCache;
import pl.jakubtworek.patterns.labs.resilience.RetryExecutor;

class ResilienceAndCacheLabTest {
    @Test
    void exponentialBackoffIsCappedAndUsesOneSharedBudget() {
        var time = new AtomicLong();
        var delays = new ArrayList<Long>();
        var attempts = new AtomicInteger();
        var retry = new RetryExecutor(4, Duration.ofNanos(10), Duration.ofNanos(25), time::get, delay -> {
            delays.add(delay);
            time.addAndGet(delay);
        }, bound -> bound);
        String result = retry.execute(Duration.ofNanos(100), error -> true, () -> {
            if (attempts.incrementAndGet() < 4) {
                throw new IllegalStateException("Transient");
            }
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(java.util.List.of(10L, 20L, 25L), delays);
        assertEquals(55, time.get());
    }

    @Test
    void exhaustedBudgetPreventsSleepAndAnotherAttempt() {
        var time = new AtomicLong();
        var attempts = new AtomicInteger();
        var retry = new RetryExecutor(
                10, Duration.ofNanos(5), Duration.ofNanos(20), time::get,
                delay -> fail("Cannot sleep past remaining budget"), bound -> bound);

        var error = assertThrows(RetryExecutor.BudgetExceeded.class,
                () -> retry.execute(Duration.ofNanos(10), failure -> true, () -> {
                    attempts.incrementAndGet();
                    time.addAndGet(7);
                    throw new IllegalStateException("Attempt consumed seven nanoseconds");
                }));
        assertEquals(1, attempts.get());
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    void permanentFailureDoesNotUseBackoff() {
        var retry = new RetryExecutor(
                3, Duration.ofNanos(1), Duration.ofNanos(4), () -> 0,
                delay -> fail("Permanent failure must not sleep"), bound -> bound);
        assertThrows(IllegalArgumentException.class, () -> retry.execute(
                Duration.ofNanos(100), failure -> !(failure instanceof IllegalArgumentException), () -> {
                    throw new IllegalArgumentException("Bad request");
                }));
    }

    @Test
    void schedulerOversleepIsCheckedBeforeNextAttempt() {
        var time = new AtomicLong();
        var attempts = new AtomicInteger();
        var retry = new RetryExecutor(
                3, Duration.ofNanos(1), Duration.ofNanos(4), time::get,
                delay -> time.addAndGet(100), bound -> 0);
        assertThrows(RetryExecutor.BudgetExceeded.class,
                () -> retry.execute(Duration.ofNanos(10), failure -> true, () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException();
                }));
        assertEquals(1, attempts.get());
    }

    @Test
    void malformedJitterCannotCreateNegativeOrUnboundedDelay() {
        var retry = new RetryExecutor(
                2, Duration.ofNanos(10), Duration.ofNanos(10), () -> 0,
                delay -> fail("Invalid delay cannot be used"), bound -> bound + 1);
        assertThrows(IllegalArgumentException.class, () -> retry.execute(Duration.ofNanos(100), failure -> true, () -> {
            throw new IllegalStateException();
        }));
    }

    @Test
    void ttlExpiresExactlyAtBoundaryAndLoadsNewSourceValue() {
        var time = new AtomicLong();
        var source = new HashMap<String, String>();
        source.put("a", "old");
        var cache = new BoundedTtlCache<>(
                2, Duration.ofNanos(10), time::get, key -> Optional.ofNullable(source.get(key)));
        assertEquals(Optional.of("old"), cache.get("a"));
        source.put("a", "new");
        time.set(9);
        assertEquals(Optional.of("old"), cache.get("a"));
        time.set(10);
        assertEquals(Optional.of("new"), cache.get("a"));
    }

    @Test
    void lruEvictsLeastRecentlyReadKeyAndBoundsMemory() {
        var loads = new HashMap<String, Integer>();
        var cache = new BoundedTtlCache<String, String>(2, Duration.ofSeconds(1), () -> 0, key -> {
            loads.merge(key, 1, Integer::sum);
            return Optional.of(key);
        });
        cache.get("a");
        cache.get("b");
        cache.get("a");
        cache.get("c");
        cache.get("b");
        assertEquals(1, loads.get("a"));
        assertEquals(2, loads.get("b"));
        assertEquals(2, cache.size());
    }

    @Test
    void negativeCachingAvoidsRepeatedMissesUntilInvalidation() {
        var loads = new AtomicInteger();
        var cache = new BoundedTtlCache<String, String>(2, Duration.ofSeconds(1), () -> 0, key -> {
            loads.incrementAndGet();
            return Optional.empty();
        });
        assertTrue(cache.get("missing").isEmpty());
        assertTrue(cache.get("missing").isEmpty());
        assertEquals(1, loads.get());
        cache.invalidate("missing");
        cache.get("missing");
        assertEquals(2, loads.get());
    }

    @Test
    void failedExplicitRefreshDoesNotDestroyStillFreshCopy() {
        var loads = new AtomicInteger();
        var cache = new BoundedTtlCache<String, String>(1, Duration.ofSeconds(1), () -> 0, key -> {
            if (loads.incrementAndGet() > 1) {
                throw new IllegalStateException("Source unavailable");
            }
            return Optional.of("old");
        });
        cache.get("a");
        assertThrows(IllegalStateException.class, () -> cache.refresh("a"));
        assertEquals(Optional.of("old"), cache.get("a"));
    }
}
```

</details>
