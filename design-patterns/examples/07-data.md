# Cache, wersja i odtwarzanie historii

[Mapa przykładów](../LABORATORIA.md) · [Karty wzorców](../catalog/07-persistence/README.md)

Ten dokument zawiera rozwinięty model i scenariusze jego poprawności w blokach Markdown. Karty katalogu
pozostają punktem wejścia do intuicji, konkretnych zastosowań, kosztu i kontrprzykładów. Modele mają zakres
lokalny i in-memory; trwałość oraz granice rozproszone wymagają osobnego kontraktu.

## Model

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: DataPatterns</summary>

```java
package pl.jakubtworek.patterns.systems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Trzy małe modele zarządzania kopią danych, konfliktem i historią.
 * Modele nie wykonują I/O: test kontroluje dokładny moment zmiany źródła i projekcji.
 */
public final class DataPatterns {
    private DataPatterns() {
    }

    // CACHE-ASIDE: aplikacja zna źródło prawdy; zapis usuwa lokalną kopię.
    public static final class CacheAside<K, V> {
        private final Map<K, V> cache = new HashMap<>();
        private final Function<K, V> source;

        public CacheAside(Function<K, V> source) {
            this.source = source;
        }

        public V get(K key) {
            return cache.computeIfAbsent(key, source);
        }

        public void invalidate(K key) {
            cache.remove(key);
        }
    }

    // OPTIMISTIC OFFLINE LOCK: compare-and-set wersji odrzuca zapis na nieaktualnym snapshotcie.
    public record Versioned(long version, long value) {
    }

    public static final class OptimisticCounter {
        private Versioned current = new Versioned(0, 0);

        public synchronized Versioned read() {
            return current;
        }

        public synchronized void update(long expectedVersion, long newValue) {
            // Ten sam monitor obejmuje odczyt wersji i zapis. Dwa niezależne synchronized get/set nie wystarczą.
            if (current.version() != expectedVersion) {
                throw new IllegalStateException("stale version");
            }
            current = new Versioned(Math.incrementExact(expectedVersion), newValue);
        }
    }

    // EVENT SOURCING + CQRS: źródłem prawdy jest log; projekcja może być opóźniona i odbudowana.
    public record Deposited(long sequence, long cents) {
    }

    public static final class Account {
        private final List<Deposited> history = new ArrayList<>();

        public void deposit(long cents) {
            if (cents <= 0) {
                throw new IllegalArgumentException("positive deposit");
            }
            history.add(new Deposited(history.size() + 1L, cents));
        }

        public List<Deposited> history() {
            return List.copyOf(history);
        }
    }

    public static final class BalanceProjection {
        private long sequence;
        private long cents;

        public void apply(Deposited event) {
            // Powtórzony prefiks tej samej historii.
            if (event.sequence() <= sequence) {
                return;
            }
            if (event.sequence() != sequence + 1) {
                throw new IllegalStateException("gap in history");
            }
            // Obliczenie może rzucić overflow. Wersję przesuwamy dopiero po uzyskaniu poprawnego salda.
            long next = Math.addExact(cents, event.cents());
            cents = next;
            sequence = event.sequence();
        }

        public long cents() {
            return cents;
        }
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: DataPatternsTest</summary>

```java
package pl.jakubtworek.patterns;

import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.patterns.systems.DataPatterns.*;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataPatternsTest {
    @Test
    void cacheAsideServesStaleValueUntilInvalidated() {
        var source = new HashMap<String, String>();
        var loads = new AtomicInteger();
        source.put("p1", "old");
        var cache = new CacheAside<String, String>(key -> {
            loads.incrementAndGet();
            return source.get(key);
        });
        assertEquals("old", cache.get("p1"));
        source.put("p1", "new");
        assertEquals("old", cache.get("p1"));
        assertEquals(1, loads.get());
        cache.invalidate("p1");
        assertEquals("new", cache.get("p1"));
        assertEquals(2, loads.get());
    }

    @Test
    void optimisticLockRejectsLostUpdate() {
        var counter = new OptimisticCounter();
        var first = counter.read();
        var stale = counter.read();
        counter.update(first.version(), first.value() + 1);
        assertThrows(IllegalStateException.class, () -> counter.update(stale.version(), stale.value() + 1));
        assertEquals(new Versioned(1, 1), counter.read());
    }

    @Test
    void eventLogRebuildsLaggingProjectionAndIgnoresDuplicatePrefix() {
        var account = new Account();
        var projection = new BalanceProjection();
        account.deposit(100);
        account.deposit(200);
        assertEquals(0, projection.cents());
        account.history().forEach(projection::apply);
        account.history().forEach(projection::apply);
        assertEquals(300, projection.cents());
        var rebuilt = new BalanceProjection();
        account.history().forEach(rebuilt::apply);
        assertEquals(projection.cents(), rebuilt.cents());
    }

    @Test
    void projectionRejectsGapAndOverflowWithoutAdvancingState() {
        var projection = new BalanceProjection();
        assertThrows(IllegalStateException.class, () -> projection.apply(new Deposited(2, 100)));
        projection.apply(new Deposited(1, Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> projection.apply(new Deposited(2, 1)));
        projection.apply(new Deposited(2, 0));
        assertEquals(Long.MAX_VALUE, projection.cents());
    }
}
```

</details>
