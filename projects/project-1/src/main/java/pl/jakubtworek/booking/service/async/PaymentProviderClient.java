package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Klient symulujący zewnętrznego providera płatności.
 *
 * To nie jest prawdziwa integracja z systemem płatności.
 * Klasa służy edukacyjnie do pokazania różnych scenariuszy asynchronicznych:
 *
 * - płatność zaakceptowana,
 * - płatność odrzucona,
 * - awaria techniczna providera,
 * - wolna odpowiedź providera,
 * - timeout,
 * - anulowanie zadania,
 * - obsługa InterruptedException.
 *
 * Dzięki temu AsyncReservationService może testować zachowanie flow bez
 * integrowania się z realnym API płatniczym.
 */
@Service
public class PaymentProviderClient {

    /**
     * Executor używany do uruchamiania symulowanej walidacji płatności.
     *
     * Wstrzykujemy bookingAsyncExecutor, żeby nie używać domyślnego
     * ForkJoinPool.commonPool().
     */
    private final ThreadPoolExecutor executor;

    /**
     * Flaga diagnostyczna używana w testach.
     *
     * Pozwala sprawdzić, czy wolna operacja płatności została przerwana
     * po anulowaniu future.
     *
     * AtomicBoolean jest użyty, ponieważ wartość może być zapisywana i odczytywana
     * przez różne wątki.
     */
    private final AtomicBoolean lastSlowCallInterrupted = new AtomicBoolean(false);

    /**
     * Constructor injection.
     *
     * Parametr bookingAsyncExecutor powinien odpowiadać beanowi z AsyncExecutorConfig.
     */
    public PaymentProviderClient(ThreadPoolExecutor bookingAsyncExecutor) {
        this.executor = bookingAsyncExecutor;
    }

    /**
     * Uruchamia asynchroniczną walidację płatności.
     *
     * Zwraca CompletableFuture, które zakończy się:
     *
     * - APPROVED — gdy płatność jest zaakceptowana,
     * - DECLINED — gdy płatność jest odrzucona biznesowo,
     * - wyjątkiem technicznym — gdy provider "psuje się",
     * - wynikiem po długim czasie — dla scenariusza SLOW.
     *
     * Ta metoda ręcznie tworzy CompletableFuture i wiąże je z Future zwróconym
     * przez executor.submit(...), żeby można było próbować anulować działające
     * zadanie przez runningTask.cancel(true).
     */
    public CompletableFuture<PaymentValidationResult> validatePayment(UUID reservationId,
                                                                      PaymentScenario scenario) {
        /*
         * promise to CompletableFuture zwracane klientowi tej metody.
         *
         * Nie używamy tutaj od razu CompletableFuture.supplyAsync(...), bo chcemy
         * mieć uchwyt do Future<?> runningTask i móc spróbować przerwać zadanie.
         */
        CompletableFuture<PaymentValidationResult> promise = new CompletableFuture<>();

        /*
         * Uruchamiamy zadanie w kontrolowanej puli wątków.
         */
        Future<?> runningTask = executor.submit(() -> {
            try {
                /*
                 * Symulujemy różne zachowania providera płatności.
                 */
                PaymentValidationResult result = switch (scenario) {
                    case APPROVED -> PaymentValidationResult.getApproved();

                    case DECLINED -> PaymentValidationResult.declined("PAYMENT_DECLINED");

                    case FAILING -> throw new IllegalStateException("Payment provider technical failure");

                    case SLOW -> slowPaymentValidation();
                };

                /*
                 * Jeśli zadanie zakończyło się poprawnie, kończymy promise sukcesem.
                 *
                 * Jeżeli promise został wcześniej zakończony timeoutem albo anulowany,
                 * complete(...) po prostu zwróci false i nie zmieni już wyniku.
                 */
                promise.complete(result);
            } catch (InterruptedException e) {
                /*
                 * Wolna walidacja może zostać przerwana przez cancel(true).
                 *
                 * Poprawna praktyka:
                 * - przywrócić flagę przerwania,
                 * - zakończyć promise wyjątkiem.
                 */
                Thread.currentThread().interrupt();
                lastSlowCallInterrupted.set(true);

                promise.completeExceptionally(
                        new CancellationException("Payment validation interrupted")
                );
            } catch (Throwable error) {
                /*
                 * Każdy inny błąd providera kończy promise wyjątkiem.
                 */
                promise.completeExceptionally(error);
            }
        });

        /*
         * Jeśli ktoś anuluje promise, próbujemy anulować także realnie uruchomione
         * zadanie w executorze.
         *
         * runningTask.cancel(true) wysyła interrupt do wątku wykonującego zadanie.
         *
         * Uwaga:
         * interrupt zadziała tylko wtedy, gdy wykonywany kod reaguje na przerwanie.
         * W tym przykładzie Thread.sleep(...) reaguje InterruptedException.
         */
        promise.whenComplete((result, error) -> {
            if (promise.isCancelled()) {
                runningTask.cancel(true);
            }
        });

        return promise;
    }

    /**
     * Symuluje wolną odpowiedź providera płatności.
     *
     * Sleep trwa 5 sekund, czyli dłużej niż timeout ustawiony w AsyncReservationService.
     *
     * Dzięki temu można sprawdzić scenariusz:
     * - provider nie odpowiada w 2 sekundy,
     * - flow przechodzi do PAYMENT_TIMEOUT,
     * - oryginalna operacja jest anulowana/przerywana.
     */
    private PaymentValidationResult slowPaymentValidation() throws InterruptedException {
        lastSlowCallInterrupted.set(false);

        Thread.sleep(5_000);

        return PaymentValidationResult.getApproved();
    }

    /**
     * Zwraca informację diagnostyczną dla testów.
     *
     * Pozwala sprawdzić, czy ostatnia wolna walidacja płatności została przerwana.
     */
    public boolean wasLastSlowCallInterrupted() {
        return lastSlowCallInterrupted.get();
    }
}