package pl.jakubtworek.booking.education.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Laboratorium wirtualnych wątków osadzone w domenie rezerwacji.
 *
 * <p>Wirtualny wątek obniża koszt oczekiwania na blokujące I/O, ale nie zwiększa
 * przepustowości bazy danych. Dlatego liczba uruchomionych zadań może być duża,
 * natomiast wejście do ograniczonego downstreamu nadal chroni {@link Semaphore}.
 * Bez tego rozdzielenia tysiące tanich wątków mogłyby jednocześnie zająć całą
 * pulę połączeń JDBC i pogorszyć latency całej aplikacji.</p>
 */
public final class VirtualThreadReservationBatch {

    private final Semaphore downstreamPermits;

    public VirtualThreadReservationBatch(int downstreamConcurrency) {
        if (downstreamConcurrency < 1) {
            throw new IllegalArgumentException("downstreamConcurrency must be positive");
        }
        this.downstreamPermits = new Semaphore(downstreamConcurrency, true);
    }

    /**
     * Uruchamia blokujące operacje na wirtualnych wątkach i zachowuje kolejność
     * wyników zgodną z kolejnością wejścia. {@code Future#get()} nie tworzy tu
     * gwarancji wydajnościowej — służy jedynie do zebrania zakończonego batcha.
     *
     * <p>Przerwanie jest częścią kontraktu. Po otrzymaniu interruptu anulujemy
     * pozostałe zadania przez {@code shutdownNow()}, przywracamy flagę przerwania
     * i kończymy operację. Połknięcie {@link InterruptedException} powodowałoby,
     * że timeout requestu nie zatrzyma pracy aplikacji.</p>
     */
    public <T> List<T> execute(List<? extends Callable<T>> operations) throws InterruptedException {
        Objects.requireNonNull(operations, "operations");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = operations.stream()
                    .map(operation -> executor.submit(() -> withDownstreamPermit(operation)))
                    .toList();

            List<T> results = new ArrayList<>(futures.size());
            try {
                for (var future : futures) {
                    results.add(future.get());
                }
                return List.copyOf(results);
            } catch (InterruptedException interrupted) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (ExecutionException failedOperation) {
                executor.shutdownNow();
                Throwable cause = failedOperation.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Reservation operation failed", cause);
            }
        }
    }

    private <T> T withDownstreamPermit(Callable<T> operation) throws Exception {
        downstreamPermits.acquire();
        try {
            return operation.call();
        } finally {
            // Permit musi zostać zwolniony także po wyjątku. Inaczej pojedyncza
            // awaria stopniowo wyczerpałaby limit i zatrzymała cały batch.
            downstreamPermits.release();
        }
    }
}
