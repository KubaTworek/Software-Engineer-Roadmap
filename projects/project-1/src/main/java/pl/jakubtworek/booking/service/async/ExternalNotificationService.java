package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.OutboundMessage;
import pl.jakubtworek.booking.repository.OutboundMessageRepository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Serwis odpowiedzialny za asynchroniczne powiadamianie systemów zewnętrznych.
 *
 * To jest side-effect wykonywany po potwierdzeniu rezerwacji.
 *
 * W tym projekcie nie wysyłamy prawdziwego webhooka.
 * Zamiast tego symulujemy powiadomienie przez:
 *
 * - opóźnienie Thread.sleep(...),
 * - zapis OutboundMessage do bazy.
 *
 * Klasa pokazuje:
 *
 * - równoległe uruchomienie dwóch providerów,
 * - użycie CompletableFuture,
 * - użycie applyToEither jako wariantu anyOf,
 * - fallback po błędzie,
 * - zapis technicznego komunikatu wychodzącego.
 */
@Service
public class ExternalNotificationService {

    /**
     * Repozytorium komunikatów wychodzących.
     *
     * Służy do zapisania informacji, czy powiadomienie zewnętrznego systemu
     * zostało wykonane poprawnie, czy zakończyło się błędem.
     */
    private final OutboundMessageRepository outboundMessageRepository;

    /**
     * Executor dla operacji asynchronicznych.
     *
     * Używamy własnej puli bookingAsyncExecutor, zamiast domyślnego commonPool.
     * Dzięki temu łatwiej kontrolować liczbę wątków, kolejkę i shutdown.
     */
    private final ThreadPoolExecutor executor;

    /**
     * Constructor injection.
     *
     * Parametr bookingAsyncExecutor powinien wskazywać na bean z AsyncExecutorConfig.
     */
    public ExternalNotificationService(OutboundMessageRepository outboundMessageRepository,
                                       ThreadPoolExecutor bookingAsyncExecutor) {
        this.outboundMessageRepository = outboundMessageRepository;
        this.executor = bookingAsyncExecutor;
    }

    /**
     * Powiadamia systemy zewnętrzne po potwierdzeniu rezerwacji.
     *
     * Uruchamiamy dwóch providerów równolegle:
     *
     * - WEBHOOK_A z większą latencją,
     * - WEBHOOK_B z mniejszą latencją.
     *
     * Następnie używamy applyToEither(...), czyli bierzemy wynik tego providera,
     * który zakończy się jako pierwszy.
     *
     * To pokazuje wzorzec podobny do fan-out / first-success-wins.
     */
    public CompletableFuture<SideEffectResult> notifyExternalSystems(ReservationResponse reservation) {
        CompletableFuture<SideEffectResult> providerA = CompletableFuture.supplyAsync(
                () -> notifyProvider(reservation, "WEBHOOK_A", 80),
                executor
        );

        CompletableFuture<SideEffectResult> providerB = CompletableFuture.supplyAsync(
                () -> notifyProvider(reservation, "WEBHOOK_B", 20),
                executor
        );

        return providerA
                /*
                 * applyToEither kończy wynik, gdy jeden z dwóch future zakończy się sukcesem.
                 *
                 * W tej implementacji nie czekamy na oba providery.
                 * Zwracamy pierwszy poprawny wynik.
                 *
                 * Uwaga:
                 * drugi provider może nadal działać w tle i też zapisać OutboundMessage.
                 * applyToEither nie anuluje automatycznie przegranego future.
                 */
                .applyToEither(providerB, result -> result)

                /*
                 * Jeśli oba flow zakończą się błędem tak, że wynikowy future też będzie
                 * błędny, zapisujemy nieudaną notyfikację.
                 */
                .exceptionally(error -> saveNotificationFailure(reservation, error));
    }

    /**
     * Symuluje powiadomienie jednego providera.
     *
     * @Transactional sugeruje, że zapis OutboundMessage powinien być transakcyjny.
     *
     * Uwaga o proxy:
     * ta metoda jest wywoływana z lambdy w tej samej klasie, więc wywołanie może
     * nie przejść przez proxy Springa. Wtedy @Transactional może nie zadziałać.
     */
    @Transactional
    public SideEffectResult notifyProvider(ReservationResponse reservation,
                                           String channel,
                                           long latencyMs) {
        try {
            /*
             * Symulowana latencja zewnętrznego systemu.
             *
             * To jest tylko materiał edukacyjny. W prawdziwym kodzie byłoby tutaj
             * wywołanie HTTP, klient SDK albo publikacja komunikatu.
             */
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            /*
             * Poprawna obsługa InterruptedException:
             * przywracamy flagę przerwania i kończymy metodę wyjątkiem.
             */
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Notification interrupted", e);
        }

        /*
         * Zapisujemy udaną notyfikację.
         *
         * channel pozwala zobaczyć, który provider zakończył się sukcesem.
         */
        outboundMessageRepository.save(OutboundMessage.sent(
                reservation.id(),
                channel,
                "External notification for reservation " + reservation.id()
        ));

        return SideEffectResult.success(channel);
    }

    /**
     * Zapisuje informację o błędzie notyfikacji.
     *
     * To jest fallback dla błędów providerów.
     *
     * Podobnie jak notifyProvider(...), ta metoda ma @Transactional, ale przy
     * wywołaniu z tej samej klasy może ominąć proxy Springa.
     */
    @Transactional
    public SideEffectResult saveNotificationFailure(ReservationResponse reservation, Throwable error) {
        outboundMessageRepository.save(OutboundMessage.failed(
                reservation.id(),
                "WEBHOOK",
                "External notification for reservation " + reservation.id(),
                error
        ));

        return SideEffectResult.failure("notification", error);
    }
}