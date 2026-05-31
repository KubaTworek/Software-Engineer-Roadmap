package pl.jakubtworek.booking.service.async;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * Pomocnicza klasa do nakładania timeoutu na CompletableFuture.
 *
 * CompletableFuture sam reprezentuje wynik operacji asynchronicznej,
 * ale czasem potrzebujemy ograniczyć maksymalny czas oczekiwania na wynik.
 *
 * Przykład z projektu:
 *
 * - wysyłamy zapytanie do payment providera,
 * - jeśli odpowie szybko, kontynuujemy flow,
 * - jeśli nie odpowie w 2 sekundy, kończymy operację timeoutem,
 * - rezerwacja może zostać oznaczona jako PAYMENT_TIMEOUT.
 *
 * Ta klasa jest package-private i final, bo jest technicznym helperem
 * używanym tylko wewnątrz pakietu async.
 */
final class CompletableFutureTimeouts {

    /**
     * Prywatny konstruktor blokuje tworzenie instancji.
     *
     * Klasa ma wyłącznie metody statyczne.
     */
    private CompletableFutureTimeouts() {
    }

    /**
     * Zwraca nowe CompletableFuture, które zakończy się:
     *
     * - sukcesem, jeśli original zakończy się przed timeoutem,
     * - błędem, jeśli original zakończy się błędem przed timeoutem,
     * - TimeoutException, jeśli timeout minie jako pierwszy.
     *
     * Nie blokujemy żadnego wątku oczekiwaniem.
     * Zamiast tego scheduler planuje zadanie, które po czasie timeoutu
     * spróbuje zakończyć wynikowy CompletableFuture wyjątkiem.
     */
    static <T> CompletableFuture<T> withTimeout(
            CompletableFuture<T> original,
            Duration timeout,
            ScheduledExecutorService scheduler
    ) {
        /*
         * result to future zwracany na zewnątrz.
         *
         * Nie zwracamy bezpośrednio original, bo chcemy kontrolować dodatkowy warunek:
         * "zakończ się timeoutem, jeśli original trwa za długo".
         */
        CompletableFuture<T> result = new CompletableFuture<>();

        /*
         * Planowane zadanie timeoutu.
         *
         * Po upływie timeout.toMillis() scheduler wykona lambdę.
         */
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            TimeoutException timeoutException =
                    new TimeoutException("Operation timed out after " + timeout);

            /*
             * completeExceptionally(...) zwraca true tylko wtedy, gdy result nie był
             * wcześniej zakończony.
             *
             * To zabezpiecza przed wyścigiem:
             * - original kończy się prawie w tym samym momencie,
             * - timeout też próbuje zakończyć result.
             *
             * Tylko jedna strona wygra.
             */
            if (result.completeExceptionally(timeoutException)) {
                /*
                 * Jeśli timeout wygrał, próbujemy anulować oryginalną operację.
                 *
                 * Uwaga:
                 * cancel(true) nie gwarantuje zatrzymania pracy.
                 * Dla CompletableFuture uruchomionych przez supplyAsync anulowanie
                 * nie zawsze przerwie wykonujący się kod, zwłaszcza jeśli kod nie
                 * reaguje na interrupt.
                 *
                 * To jest sygnał anulowania, nie magiczne ubicie wątku.
                 */
                original.cancel(true);
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        /*
         * Reagujemy na zakończenie original.
         *
         * whenComplete wykona się zarówno przy sukcesie, jak i przy błędzie.
         */
        original.whenComplete((value, error) -> {
            /*
             * Skoro original już się zakończył, timeout nie jest już potrzebny.
             *
             * cancel(false) oznacza:
             * - anuluj zadanie timeoutu, jeśli jeszcze nie wystartowało,
             * - nie przerywaj go, jeśli już działa.
             */
            timeoutTask.cancel(false);

            if (error == null) {
                /*
                 * Original zakończył się sukcesem.
                 *
                 * Próba complete(...) może nic nie zrobić, jeśli timeout zdążył
                 * wcześniej zakończyć result wyjątkiem.
                 */
                result.complete(value);
            } else {
                /*
                 * Original zakończył się błędem.
                 *
                 * Propagujemy ten błąd do result, o ile timeout nie wygrał wcześniej.
                 */
                result.completeExceptionally(error);
            }
        });

        return result;
    }
}