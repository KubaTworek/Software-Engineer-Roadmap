# Własność zasobu, właściciel stanu i wspólna praca

[Lista laboratoriów](README.md) · [Java](../catalog/04-java/README.md) ·
[Współbieżność](../catalog/17-concurrency/README.md)

**Modele w tym dokumencie:** [ResourcePool](05-concurrency.md#model-resourcepool),
[BoundedMailbox](05-concurrency.md#model-boundedmailbox),
[SingleFlight](05-concurrency.md#model-singleflight). **Scenariusze poprawności:**
[dane, operacje i oczekiwane wyniki](05-concurrency.md#scenariusze-poprawności).

## Pula: slot nie może dać dwóch właścicieli

ResourcePool dostaje już utworzone zasoby. Kontrola tożsamości zabrania umieszczenia tej samej instancji w
dwóch slotach. ArrayBlockingQueue ogranicza liczbę dostępnych obiektów, a poll z timeout kończy oczekiwanie na
lease. Ten termin nie ogranicza czasu wykonywania pracy po uzyskaniu zasobu.

Lease ma AtomicBoolean returned: pierwsze close oddaje zasób, następne niczego nie zmieniają. Bez bramki
dwukrotne close mogłoby zwrócić obiekt dwóm klientom. Value po close rzuca błąd, a use kontroluje zakres przez
try-with-resources, więc wyjątek callbacku także zwraca slot. Test wyczerpuje pulę, sprawdza timeout, oddaje
zasób dwukrotnie i potwierdza jeden wolny slot.

**Granica własności:** Java nie odbiera wcześniej zwróconej referencji R. Klient musi przestrzegać zakończenia
loan i nie używać obiektu po close. Value i close z dwóch wątków nie tworzą bezpiecznego dalszego użycia
zasobu. Demo nie resetuje brudnego stanu, nie zastępuje uszkodzonych obiektów i nie zamyka fizycznie całej
puli. Koszt kolejki jest O(1), pamięć O(liczby zasobów).

## Skrzynka: serializacja i skończony backlog

BoundedMailbox tworzy stan na jednym workerze i przekazuje go kolejnym komendom w FIFO. Callback nie może
wynieść mutowalnej referencji stanu do innych wątków. Future oddziela moment przyjęcia od wykonania;
odrzucenie także kończy Future, więc klient nie czeka na zadanie, które nigdy nie trafiło do kolejki.

Test używa dwóch CountDownLatch. Pierwszy potwierdza rozpoczęcie pracy, drugi zatrzymuje jej zakończenie.
Jedna kolejna komenda wypełnia skrzynkę, trzecia jest odrzucana. Zwolnienie latch pozwala wykonać dwie
przyjęte komendy dokładnie w tej kolejności. Nie polegamy na trafieniu w wyścig przez Thread.sleep.

Close rozpoczyna graceful shutdown: blokuje nowe przyjęcia i opróżnia kolejkę, ale nie czeka. Właściciel
osobno wywołuje awaitTermination. Nie ma wymuszonego zatrzymania; niekończący się callback może utrzymać
worker. Cancel Future nie usuwa komendy z kolejki i nie dowodzi, że jej efekt nie nastąpił. Zwykły wyjątek
komendy kończy jej wynik, lecz nie blokuje następnej pracy.

## Single-flight: wspólny load, prywatne anulowanie

PutIfAbsent rezerwuje jeden Future dla klucza. Tylko zwycięzca uruchamia loader, pozostali otrzymują kopie
zależne od wspólnego wyniku. Loader jest wywołany poza compute mapy, aby kosztowne rozpoczęcie I/O nie
działało w jej sekcji obliczenia.

Fragment scenariusza poprawności; potrzebne typy i kontekst są dalej w tym dokumencie:

```java
var flight = new SingleFlight<String, String>();
var backend = new CompletableFuture<String>();
var first = flight.execute("p1", () -> backend);
var second = flight.execute("p1", () -> fail("Already in flight"));

first.cancel(false);
backend.complete("value");

assertEquals("value", second.join());
assertEquals(0, flight.inFlightCount());
```

Cancel jednej kopii nie anuluje pozostałych. Wpis znika po sukcesie, błędzie asynchronicznym i wyjątku
uruchomienia loadera. Następne żądanie rozpoczyna nowy load: to współdzielenie pracy w toku, nie cache
ukończonych wartości.

Nie ma limitu różnych kluczy, czasu loadera i liczby czekających klientów. Nigdy nieukończony Future zachowa
wpis, więc serwis potrzebowałby deadline i admission control. Lokalny mechanizm nie łączy pracy innych
procesów JVM.

## Model ResourcePool

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: ResourcePool</summary>

```java
package pl.jakubtworek.patterns.labs.concurrency;

import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Ograniczona pula już utworzonych zasobów: Loan, Object Pool i Execute Around.
 */
public final class ResourcePool<R> {
    private final ArrayBlockingQueue<R> available;

    public ResourcePool(List<R> resources) {
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("At least one resource required");
        }
        var identities = Collections.newSetFromMap(new IdentityHashMap<R, Boolean>());
        for (R resource : resources) {
            if (!identities.add(Objects.requireNonNull(resource))) {
                throw new IllegalArgumentException("The same instance cannot occupy two slots");
            }
        }
        available = new ArrayBlockingQueue<>(resources.size(), true, resources);
    }

    public final class Lease implements AutoCloseable {
        private final R resource;
        private final AtomicBoolean returned = new AtomicBoolean();

        private Lease(R resource) {
            this.resource = resource;
        }

        public R value() {
            if (returned.get()) {
                throw new IllegalStateException("Lease already returned");
            }
            return resource;
        }

        @Override
        public void close() {
            // Idempotentne close nie może dodać tego samego obiektu drugi raz do kolejki.
            if (returned.compareAndSet(false, true)) {
                available.add(resource);
            }
        }
    }

    public Lease acquire(Duration timeout) throws InterruptedException, TimeoutException {
        long nanos = timeout.toNanos();
        if (nanos < 0) {
            throw new IllegalArgumentException("Negative acquisition timeout");
        }
        R resource = available.poll(nanos, TimeUnit.NANOSECONDS);
        if (resource == null) {
            throw new TimeoutException("No resource available");
        }
        return new Lease(resource);
    }

    public <T> T use(Duration timeout, Function<R, T> work) throws InterruptedException, TimeoutException {
        try (Lease lease = acquire(timeout)) {
            return work.apply(lease.value());
        }
    }

    public int availableCount() {
        return available.size();
    }
}
```

</details>

## Model BoundedMailbox

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: BoundedMailbox</summary>

```java
package pl.jakubtworek.patterns.labs.concurrency;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Active Object: jeden worker posiada stan, a ograniczona skrzynka przenosi polecenia.
 */
public final class BoundedMailbox<S> implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Supplier<S> initialState;
    private S state;
    private boolean initialized;

    public BoundedMailbox(int queueCapacity, Supplier<S> initialState) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("Positive mailbox capacity required");
        }
        this.initialState = Objects.requireNonNull(initialState);
        executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name("pattern-mailbox-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> CompletableFuture<T> submit(Function<S, T> command) {
        Objects.requireNonNull(command);
        var result = new CompletableFuture<T>();
        try {
            executor.execute(() -> {
                try {
                    if (!initialized) {
                        state = Objects.requireNonNull(initialState.get());
                        initialized = true;
                    }
                    result.complete(command.apply(state));
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                    if (failure instanceof Error fatal) {
                        throw fatal;
                    }
                }
            });
        } catch (RejectedExecutionException fullOrClosed) {
            // Każde submit ma wynik: odrzucone polecenie nie zostaje wiecznie nieukończonym Future.
            result.completeExceptionally(fullOrClosed);
        }
        return result;
    }

    @Override
    public void close() {
        // Graceful shutdown: odrzucamy nowe komendy, wcześniej przyjęte pozostają w FIFO.
        // close nie czeka. Właściciel jawnie korzysta z awaitTermination z własnym budżetem.
        executor.shutdown();
    }

    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Negative termination timeout");
        }
        return executor.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }
}
```

</details>

## Model SingleFlight

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: SingleFlight</summary>

```java
package pl.jakubtworek.patterns.labs.concurrency;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Współdzieli wyłącznie pracę w locie; zakończony wynik nie jest cacheowany.
 */
public final class SingleFlight<K, V> {
    private final ConcurrentHashMap<K, CompletableFuture<V>> running = new ConcurrentHashMap<>();

    public CompletableFuture<V> execute(K key, Supplier<? extends CompletionStage<V>> loader) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(loader);
        var candidate = new CompletableFuture<V>();
        CompletableFuture<V> existing = running.putIfAbsent(key, candidate);
        if (existing != null) {
            return existing.copy();
        }
        // Tylko zwycięzca putIfAbsent uruchamia I/O. Loader nie działa wewnątrz blokady compute.
        try {
            Objects.requireNonNull(loader.get()).whenComplete((value, failure) -> {
                running.remove(key, candidate);
                if (failure == null) {
                    candidate.complete(value);
                } else {
                    candidate.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) {
            running.remove(key, candidate);
            candidate.completeExceptionally(failure);
            if (failure instanceof Error fatal) {
                throw fatal;
            }
        }
        // Klient dostaje zależną kopię Future. Jego cancel nie anuluje pracy innych odbiorców.
        return candidate.copy();
    }

    public int inFlightCount() {
        return running.size();
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: ConcurrencyLabTest</summary>

```java
package pl.jakubtworek.patterns.labs;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.jakubtworek.patterns.labs.concurrency.BoundedMailbox;
import pl.jakubtworek.patterns.labs.concurrency.ResourcePool;
import pl.jakubtworek.patterns.labs.concurrency.SingleFlight;

@Timeout(5)
class ConcurrencyLabTest {
    @Test
    void loanReturnsResourceEvenWhenClientThrows() throws Exception {
        var pool = new ResourcePool<>(List.of(new StringBuilder()));
        assertThrows(IllegalStateException.class, () -> pool.use(Duration.ZERO, resource -> {
            throw new IllegalStateException("Client failure");
        }));
        assertEquals(1, pool.availableCount());
        int length = pool.use(Duration.ZERO, resource -> resource.append("abc").length());
        assertEquals(3, length);
    }

    @Test
    void doubleCloseDoesNotDuplicateAvailableSlotAndUseAfterCloseIsRejected() throws Exception {
        var pool = new ResourcePool<>(List.of(new Object()));
        var lease = pool.acquire(Duration.ZERO);
        assertThrows(TimeoutException.class, () -> pool.acquire(Duration.ZERO));
        lease.close();
        lease.close();
        assertEquals(1, pool.availableCount());
        assertThrows(IllegalStateException.class, lease::value);
    }

    @Test
    void duplicateObjectCannotBeLeasedConcurrentlyThroughTwoSlots() {
        var same = new Object();
        assertThrows(IllegalArgumentException.class, () -> new ResourcePool<>(List.of(same, same)));
    }

    @Test
    void mailboxSerializesMutationAndDrainsAcceptedWorkDuringShutdown() throws Exception {
        var mailbox = new BoundedMailbox<>(10, AtomicInteger::new);
        try {
            var first = mailbox.submit(AtomicInteger::incrementAndGet);
            var second = mailbox.submit(AtomicInteger::incrementAndGet);
            mailbox.close();
            assertEquals(1, first.get(1, TimeUnit.SECONDS));
            assertEquals(2, second.get(1, TimeUnit.SECONDS));
            assertTrue(mailbox.awaitTermination(Duration.ofSeconds(1)));
            var rejected = assertThrows(CompletionException.class,
                    () -> mailbox.submit(AtomicInteger::incrementAndGet).join());
            assertInstanceOf(RejectedExecutionException.class, rejected.getCause());
        } finally {
            mailbox.close();
        }
    }

    @Test
    void fullMailboxRejectsWithoutExecutingOrLosingAcceptedCommands() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var mailbox = new BoundedMailbox<>(1, AtomicInteger::new);
        try {
            var first = mailbox.submit(state -> {
                started.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test did not release worker");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return state.incrementAndGet();
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            var queued = mailbox.submit(AtomicInteger::incrementAndGet);
            var rejected = mailbox.submit(AtomicInteger::incrementAndGet);
            var error = assertThrows(CompletionException.class, rejected::join);
            assertInstanceOf(RejectedExecutionException.class, error.getCause());
            release.countDown();
            assertEquals(1, first.get(1, TimeUnit.SECONDS));
            assertEquals(2, queued.get(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            mailbox.close();
            assertTrue(mailbox.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void failedMailboxCommandDoesNotPreventProcessingNextCommand() throws Exception {
        var mailbox = new BoundedMailbox<>(2, AtomicInteger::new);
        try {
            var failed = mailbox.submit(state -> {
                throw new IllegalStateException("Expected failure");
            });
            var error = assertThrows(CompletionException.class, failed::join);
            assertInstanceOf(IllegalStateException.class, error.getCause());
            assertEquals(1, mailbox.submit(AtomicInteger::incrementAndGet).get(1, TimeUnit.SECONDS));
        } finally {
            mailbox.close();
            assertTrue(mailbox.awaitTermination(Duration.ofSeconds(1)));
        }
    }

    @Test
    void singleFlightSharesWorkButCancellationIsLocalToOneCaller() {
        var flight = new SingleFlight<String, String>();
        var backend = new CompletableFuture<String>();
        var loads = new AtomicInteger();
        var first = flight.execute("p1", () -> {
            loads.incrementAndGet();
            return backend;
        });
        var second = flight.execute("p1", () -> fail("Second caller must share the first load"));
        first.cancel(false);
        backend.complete("value");
        assertTrue(first.isCancelled());
        assertEquals("value", second.join());
        assertEquals(1, loads.get());
        assertEquals(0, flight.inFlightCount());
        assertEquals("fresh", flight.execute("p1", () -> CompletableFuture.completedFuture("fresh")).join());
    }

    @Test
    void singleFlightRemovesFailedLoadSoNextRequestCanRecover() {
        var flight = new SingleFlight<String, String>();
        var backend = new CompletableFuture<String>();
        var first = flight.execute("p1", () -> backend);
        var second = flight.execute("p1", () -> fail("Already in flight"));
        backend.completeExceptionally(new IllegalStateException("Backend failed"));
        assertThrows(CompletionException.class, first::join);
        assertThrows(CompletionException.class, second::join);
        assertEquals(0, flight.inFlightCount());
        assertEquals("ok", flight.execute("p1", () -> CompletableFuture.completedFuture("ok")).join());
    }

    @Test
    void synchronousLoaderFailureAlsoReleasesSingleFlightEntry() {
        var flight = new SingleFlight<String, String>();
        var result = flight.execute("p1", () -> {
            throw new IllegalArgumentException("Bad configuration");
        });
        assertThrows(CompletionException.class, result::join);
        assertEquals(0, flight.inFlightCount());
    }
}
```

</details>
