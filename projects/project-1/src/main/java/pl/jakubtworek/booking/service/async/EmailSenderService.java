package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.OutboundMessage;
import pl.jakubtworek.booking.repository.OutboundMessageRepository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Serwis odpowiedzialny za asynchroniczną wysyłkę maila potwierdzającego rezerwację.
 *
 * W tym projekcie nie wysyłamy prawdziwego maila.
 * Zamiast tego zapisujemy techniczny wpis OutboundMessage w bazie.
 *
 * To pozwala testować:
 *
 * - czy side-effect został uruchomiony,
 * - czy zakończył się sukcesem,
 * - czy błąd został obsłużony,
 * - czy flow rezerwacji nie zależy bezpośrednio od powodzenia maila.
 *
 * Email jest przykładem operacji, która zwykle nie powinna blokować głównego
 * requestu dłużej niż to konieczne.
 */
@Service
public class EmailSenderService {

    /**
     * Repozytorium komunikatów wychodzących.
     *
     * Zapisujemy tutaj informację o udanej albo nieudanej próbie wysłania maila.
     */
    private final OutboundMessageRepository outboundMessageRepository;

    /**
     * Executor używany do uruchamiania operacji email w tle.
     *
     * Wstrzykujemy bookingAsyncExecutor, żeby nie korzystać z domyślnego
     * ForkJoinPool.commonPool().
     */
    private final ThreadPoolExecutor executor;

    /**
     * Constructor injection.
     *
     * Parametr bookingAsyncExecutor powinien odpowiadać beanowi z AsyncExecutorConfig.
     */
    public EmailSenderService(OutboundMessageRepository outboundMessageRepository,
                              ThreadPoolExecutor bookingAsyncExecutor) {
        this.outboundMessageRepository = outboundMessageRepository;
        this.executor = bookingAsyncExecutor;
    }

    /**
     * Uruchamia asynchroniczną wysyłkę maila potwierdzającego.
     *
     * CompletableFuture.supplyAsync(...) wykonuje zadanie na przekazanym executorze.
     *
     * exceptionally(...) przechwytuje błąd wysyłki i zapisuje go jako failed
     * outbound message.
     *
     * Dzięki temu błąd maila nie musi przerywać całego flow potwierdzania rezerwacji.
     */
    public CompletableFuture<SideEffectResult> sendConfirmationEmail(ReservationResponse reservation) {
        return CompletableFuture
                .supplyAsync(() -> sendEmail(reservation), executor)

                /*
                 * Jeśli sendEmail(...) rzuci wyjątek, zapisujemy informację o błędzie
                 * i zwracamy SideEffectResult.failure(...).
                 *
                 * Uwaga techniczna:
                 * saveEmailFailure(...) jest metodą tej samej klasy z @Transactional.
                 * Przy takim wywołaniu adnotacja może zostać pominięta przez proxy.
                 */
                .exceptionally(error -> saveEmailFailure(reservation, error));
    }

    /**
     * Symuluje wysłanie maila.
     *
     * W rzeczywistości metoda zapisuje OutboundMessage ze statusem SENT.
     *
     * @Transactional sugeruje, że zapis do bazy powinien być transakcyjny.
     *
     * Uwaga:
     * jeśli metoda jest wywołana z tej samej klasy bez przejścia przez proxy Springa,
     * @Transactional może nie zostać zastosowane.
     */
    @Transactional
    public SideEffectResult sendEmail(ReservationResponse reservation) {
        String payload = "Confirmation email for " + reservation.customerEmail();

        /*
         * Celowa symulacja awarii providera mailowego.
         *
         * Test może utworzyć rezerwację dla emaila zawierającego "email-fail",
         * żeby sprawdzić obsługę wyjątków i zapis OutboundMessage.failed(...).
         */
        if (reservation.customerEmail().contains("email-fail")) {
            throw new IllegalStateException("Simulated email provider failure");
        }

        /*
         * Zapisujemy informację o udanym wysłaniu.
         *
         * Nie wysyłamy prawdziwego maila, bo celem etapu jest asynchroniczność
         * i obsługa side-effectów, a nie integracja SMTP.
         */
        outboundMessageRepository.save(OutboundMessage.sent(
                reservation.id(),
                "EMAIL",
                payload
        ));

        return SideEffectResult.success("email");
    }

    /**
     * Zapisuje informację o nieudanej wysyłce maila.
     *
     * To jest fallback dla błędu w sendEmail(...).
     *
     * Dzięki temu nawet jeśli side-effect się nie uda, mamy ślad diagnostyczny
     * w tabeli outbound_messages.
     */
    @Transactional
    public SideEffectResult saveEmailFailure(ReservationResponse reservation, Throwable error) {
        outboundMessageRepository.save(OutboundMessage.failed(
                reservation.id(),
                "EMAIL",
                "Confirmation email for " + reservation.customerEmail(),
                error
        ));

        return SideEffectResult.failure("email", error);
    }
}