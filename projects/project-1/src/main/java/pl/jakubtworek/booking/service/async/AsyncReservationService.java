package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.exception.BusinessRuleException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Serwis odpowiedzialny za asynchroniczny flow potwierdzania rezerwacji.
 *
 * W odróżnieniu od zwykłego ReservationService, ten serwis pokazuje sytuację,
 * w której potwierdzenie rezerwacji zależy od operacji zewnętrznej:
 *
 * - walidacji płatności,
 * - timeoutu płatności,
 * - fallbacku po błędzie technicznym,
 * - wysłania maila,
 * - zapisu audytu,
 * - powiadomienia systemu zewnętrznego.
 *
 * Ważne: to nadal jest zwykły monolit. Asynchroniczność dzieje się wewnątrz
 * jednej aplikacji przez ExecutorService/CompletableFuture, a nie przez osobne
 * mikroserwisy czy broker wiadomości.
 */
@Service
public class AsyncReservationService {

    /**
     * Maksymalny czas oczekiwania na odpowiedź payment providera.
     *
     * Jeżeli provider nie odpowie w ciągu 2 sekund, flow nie powinien wisieć
     * bez końca. Rezerwacja zostanie oznaczona jako PAYMENT_TIMEOUT.
     */
    private static final Duration PAYMENT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Klient symulujący zewnętrzny system płatności.
     *
     * Może zwracać różne scenariusze:
     * - APPROVED,
     * - DECLINED,
     * - SLOW,
     * - FAILING.
     */
    private final PaymentProviderClient paymentProviderClient;

    /**
     * Serwis odpowiedzialny za zmianę statusu rezerwacji.
     *
     * Celowo jest oddzielony od tej klasy, bo operacje statusowe powinny być
     * transakcyjne. Dzięki temu async orchestration nie miesza się bezpośrednio
     * z logiką zapisu statusów w bazie.
     */
    private final ReservationStatusService reservationStatusService;

    /**
     * Serwis wysyłający email po potwierdzeniu rezerwacji.
     *
     * To klasyczny side-effect: nie powinien być częścią krytycznej transakcji
     * potwierdzania rezerwacji.
     */
    private final EmailSenderService emailSenderService;

    /**
     * Serwis zapisujący audyt.
     *
     * Audyt jest wykonywany jako osobna operacja asynchroniczna po potwierdzeniu
     * rezerwacji.
     */
    private final AuditLogService auditLogService;

    /**
     * Serwis powiadamiający zewnętrzne systemy.
     *
     * Podobnie jak email, to efekt uboczny po potwierdzeniu rezerwacji.
     */
    private final ExternalNotificationService externalNotificationService;

    /**
     * Osobna pula wątków dla operacji asynchronicznych booking flow.
     *
     * Nie używamy domyślnego ForkJoinPool.commonPool(), bo w aplikacji serwerowej
     * lepiej jawnie kontrolować:
     * - liczbę wątków,
     * - kolejkę,
     * - strategię odrzucania zadań,
     * - shutdown.
     */
    private final ThreadPoolExecutor executor;

    /**
     * Scheduler używany do timeoutów.
     *
     * Timeout nie powinien blokować zwykłego wątku przez Thread.sleep.
     * ScheduledExecutorService pozwala zaplanować zadanie, które zakończy
     * CompletableFuture błędem po określonym czasie.
     */
    private final ScheduledExecutorService scheduler;

    /**
     * Constructor injection.
     *
     * Wstrzyknięcie konkretnych executorów przez Springa ułatwia testowanie
     * oraz pozwala centralnie skonfigurować pulę wątków.
     */
    public AsyncReservationService(
            PaymentProviderClient paymentProviderClient,
            ReservationStatusService reservationStatusService,
            EmailSenderService emailSenderService,
            AuditLogService auditLogService,
            ExternalNotificationService externalNotificationService,
            ThreadPoolExecutor bookingAsyncExecutor,
            ScheduledExecutorService bookingScheduler
    ) {
        this.paymentProviderClient = paymentProviderClient;
        this.reservationStatusService = reservationStatusService;
        this.emailSenderService = emailSenderService;
        this.auditLogService = auditLogService;
        this.externalNotificationService = externalNotificationService;
        this.executor = bookingAsyncExecutor;
        this.scheduler = bookingScheduler;
    }

    /**
     * Potwierdza rezerwację asynchronicznie.
     *
     * Flow:
     *
     * 1. Walidacja płatności.
     * 2. Timeout po 2 sekundach.
     * 3. Jeśli płatność zaakceptowana — zmiana statusu na CONFIRMED.
     * 4. Jeśli błąd techniczny albo timeout — fallback do PAYMENT_TIMEOUT.
     * 5. Po potwierdzeniu uruchomienie side-effectów w tle.
     *
     * Metoda zwraca CompletableFuture, więc caller może:
     * - poczekać przez join/get,
     * - zarejestrować callback,
     * - połączyć wynik z innym flow.
     */
    public CompletableFuture<ReservationResponse> confirm(UUID reservationId, PaymentScenario scenario) {
        CompletableFuture<ReservationResponse> confirmation = confirmAfterPaymentValidation(reservationId, scenario);

        /*
         * thenAccept uruchamia side-effecty dopiero wtedy, gdy confirmation zakończy
         * się sukcesem.
         *
         * Jeśli confirmation zakończy się błędem biznesowym, np. DECLINED,
         * ten callback nie zostanie wykonany.
         *
         * Uwaga: runSideEffectsInBackground nie wpływa na wynik confirmation.
         * To oznacza, że błąd wysyłki maila nie psuje potwierdzenia rezerwacji.
         */
        confirmation.thenAccept(this::runSideEffectsInBackground);

        return confirmation;
    }

    /**
     * Wariant testowy/diagnostyczny, który czeka także na side-effecty.
     *
     * W normalnym endpointzie często nie chcemy czekać na email, audit i notyfikację.
     * W testach oraz edukacyjnie przydaje się jednak metoda, która pozwala sprawdzić,
     * że side-effecty naprawdę się wykonały.
     *
     * thenCompose jest tutaj ważne:
     * - confirmation zwraca ReservationResponse,
     * - runSideEffects zwraca CompletableFuture<SideEffects>,
     * - thenCompose spłaszcza wynik do jednego CompletableFuture.
     *
     * Bez thenCompose dostalibyśmy zagnieżdżony typ:
     * CompletableFuture<CompletableFuture<...>>.
     */
    public CompletableFuture<AsyncConfirmationResult> confirmAndWaitForSideEffects(UUID reservationId,
                                                                                   PaymentScenario scenario) {
        return confirmAfterPaymentValidation(reservationId, scenario)
                .thenCompose(reservation -> runSideEffects(reservation)
                        .thenApply(sideEffects -> new AsyncConfirmationResult(
                                reservation,
                                sideEffects.deliverySummary(),
                                sideEffects.auditResult()
                        )));
    }

    /**
     * Właściwy flow potwierdzenia po walidacji płatności.
     *
     * Ta metoda:
     * - odpala walidację płatności,
     * - dokłada timeout,
     * - po sukcesie zmienia status rezerwacji,
     * - po błędzie technicznym robi fallback.
     */
    private CompletableFuture<ReservationResponse> confirmAfterPaymentValidation(UUID reservationId,
                                                                                 PaymentScenario scenario) {
        /*
         * Payment provider zwraca CompletableFuture, bo symulujemy zewnętrzną,
         * potencjalnie wolną operację.
         */
        CompletableFuture<PaymentValidationResult> paymentValidation =
                paymentProviderClient.validatePayment(reservationId, scenario);

        return CompletableFutureTimeouts.withTimeout(paymentValidation, PAYMENT_TIMEOUT, scheduler)

                /*
                 * thenCompose używamy dlatego, że po udanej walidacji chcemy uruchomić
                 * kolejną operację asynchroniczną: zmianę statusu rezerwacji.
                 */
                .thenCompose(result -> {
                    if (!result.approved()) {
                        /*
                         * Odrzucona płatność to błąd biznesowy, a nie techniczny timeout.
                         * Dlatego nie chcemy oznaczać rezerwacji jako PAYMENT_TIMEOUT.
                         */
                        throw new BusinessRuleException("Payment was not approved: " + result.reason());
                    }

                    /*
                     * Zmiana statusu wykonuje się w kontrolowanej puli wątków.
                     * reservationStatusService powinien zadbać o transakcję.
                     */
                    return CompletableFuture.supplyAsync(
                            () -> reservationStatusService.confirmAfterPayment(reservationId),
                            executor
                    );
                })

                /*
                 * exceptionallyCompose pozwala obsłużyć błąd i zwrócić nowy
                 * CompletableFuture.
                 *
                 * Zwykłe exceptionally zwracałoby bezpośrednio wartość, a tutaj
                 * fallback sam jest operacją asynchroniczną.
                 */
                .exceptionallyCompose(error -> handlePaymentFailure(reservationId, error));
    }

    /**
     * Obsługuje błędy walidacji płatności.
     *
     * Rozróżniamy dwa typy błędów:
     *
     * 1. Błąd biznesowy — np. payment declined.
     *    Taki błąd propagujemy dalej.
     *
     * 2. Błąd techniczny — timeout, exception providera, niedostępność systemu.
     *    Taki błąd mapujemy na status PAYMENT_TIMEOUT.
     */
    private CompletableFuture<ReservationResponse> handlePaymentFailure(UUID reservationId, Throwable error) {
        if (isBusinessRule(error)) {
            /*
             * BusinessRuleException powinien dotrzeć do warstwy wyżej.
             * Nie robimy fallbacku do PAYMENT_TIMEOUT, bo płatność została
             * świadomie odrzucona, a nie zgubiona przez timeout.
             */
            return CompletableFuture.failedFuture(unwrapCompletionException(error));
        }

        /*
         * Fallback dla błędów technicznych.
         *
         * Zamiast zostawiać request w stanie nieokreślonym, oznaczamy rezerwację
         * jako PAYMENT_TIMEOUT.
         */
        return CompletableFuture.supplyAsync(
                () -> reservationStatusService.markPaymentTimeout(reservationId),
                executor
        );
    }

    /**
     * Uruchamia side-effecty po potwierdzeniu rezerwacji, ale nie propaguje ich błędów.
     *
     * To ważna decyzja projektowa:
     *
     * - potwierdzenie rezerwacji jest głównym procesem,
     * - email/audit/notyfikacja są procesami pobocznymi,
     * - błąd maila nie powinien cofać potwierdzenia rezerwacji.
     *
     * exceptionally(error -> null) świadomie "połyka" błąd.
     * Produkcyjnie należałoby go przynajmniej zalogować albo zapisać do retry queue.
     */
    private void runSideEffectsInBackground(ReservationResponse reservation) {
        runSideEffects(reservation).exceptionally(error -> null);
    }

    /**
     * Uruchamia side-effecty po potwierdzeniu rezerwacji.
     *
     * Pokazuje kilka elementów CompletableFuture:
     *
     * - równoległe uruchomienie emaila i notyfikacji,
     * - thenCombine do połączenia dwóch wyników,
     * - osobny audit,
     * - allOf do poczekania na wszystkie operacje.
     */
    private CompletableFuture<SideEffects> runSideEffects(ReservationResponse reservation) {
        /*
         * Email i external notification mogą działać równolegle.
         */
        CompletableFuture<SideEffectResult> email =
                emailSenderService.sendConfirmationEmail(reservation);

        CompletableFuture<SideEffectResult> notification =
                externalNotificationService.notifyExternalSystems(reservation);

        /*
         * thenCombine łączy wyniki dwóch niezależnych CompletableFuture.
         *
         * DeliverySummary powstaje dopiero wtedy, gdy email i notification
         * zakończą się sukcesem.
         */
        CompletableFuture<DeliverySummary> deliverySummary =
                email.thenCombine(notification, DeliverySummary::new);

        /*
         * Audit jest trzecią niezależną operacją.
         */
        CompletableFuture<SideEffectResult> audit =
                auditLogService.writeReservationConfirmed(reservation.id());

        /*
         * allOf czeka na zakończenie wszystkich przekazanych future.
         *
         * Sam allOf nie zwraca wyników poszczególnych operacji, dlatego po jego
         * zakończeniu używamy join() na konkretnych future.
         *
         * join() jest tu bezpieczne, bo allOf zakończył się dopiero po zakończeniu
         * deliverySummary i audit.
         */
        return CompletableFuture.allOf(deliverySummary, audit)
                .thenApply(ignored -> new SideEffects(deliverySummary.join(), audit.join()));
    }

    /**
     * Sprawdza, czy błąd jest błędem biznesowym.
     *
     * CompletableFuture często opakowuje wyjątki w CompletionException.
     * Dlatego nie wystarczy sprawdzić error instanceof BusinessRuleException.
     */
    private boolean isBusinessRule(Throwable error) {
        return unwrapCompletionException(error) instanceof BusinessRuleException;
    }

    /**
     * Rozpakowuje wyjątki techniczne dodawane przez CompletableFuture.
     *
     * CompletableFuture zwykle opakowuje oryginalny wyjątek w:
     * - CompletionException,
     * - czasem ExecutionException.
     *
     * Ta metoda schodzi po cause, żeby odnaleźć rzeczywisty wyjątek biznesowy
     * albo techniczny.
     */
    private Throwable unwrapCompletionException(Throwable error) {
        Throwable current = error;

        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                return current;
            }
            current = current.getCause();
        }

        return current;
    }

    /**
     * Prywatny rekord pomocniczy łączący wyniki side-effectów.
     *
     * Nie jest częścią publicznego API. Służy tylko do uporządkowania wyniku
     * wewnątrz AsyncReservationService.
     */
    private record SideEffects(DeliverySummary deliverySummary, SideEffectResult auditResult) {
    }
}