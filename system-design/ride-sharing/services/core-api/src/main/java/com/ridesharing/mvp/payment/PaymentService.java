package com.ridesharing.mvp.payment;

import com.ridesharing.mvp.outbox.OutboxService;
import com.ridesharing.mvp.ride.Ride;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Serwis domenowy odpowiedzialny za płatności powiązane z przejazdem.
 *
 * W aplikacji ride-sharing PaymentService obsługuje dwa główne kroki:
 * - authorize: autoryzacja/blokada środków przed lub na początku przejazdu,
 * - capture: finalne pobranie środków po zakończeniu przejazdu.
 *
 * Ta klasa koordynuje zapis płatności w bazie, wywołanie providera płatniczego
 * oraz zapis eventów outbox dla dalszych systemów, np. notyfikacji, analityki,
 * reconciliation albo data warehouse.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    /**
     * Repozytorium płatności.
     *
     * Każdy przejazd powinien mieć maksymalnie jedną główną płatność.
     * Dlatego authorize() najpierw sprawdza, czy płatność dla rideId już istnieje.
     */
    private final PaymentRepository payments;

    /**
     * Adapter do providera płatności.
     *
     * W MVP może to być MockPaymentProvider.
     * W stabilniejszej wersji jest to ResilientPaymentProvider z retry/circuit breakerem.
     *
     * PaymentService nie powinien znać szczegółów konkretnego operatora płatności.
     */
    private final PaymentProvider provider;

    /**
     * Outbox dla eventów płatniczych.
     *
     * Po każdej istotnej zmianie statusu płatności zapisujemy event.
     * OutboxPublisher opublikuje go później do Kafki.
     */
    private final OutboxService outbox;

    /**
     * Autoryzuje płatność dla przejazdu.
     *
     * Flow:
     * 1. Sprawdza, czy płatność dla tego przejazdu już istnieje.
     * 2. Jeżeli istnieje, zwraca ją bez ponownego wywołania providera.
     * 3. Buduje idempotencyKey na podstawie rideId.
     * 4. Woła provider.authorize().
     * 5. Tworzy rekord Payment ze statusem AUTHORIZED albo AUTHORIZATION_FAILED.
     * 6. Zapisuje event outbox PaymentAuthorized / PaymentAuthorizationFailed.
     *
     * To zabezpiecza przed podwójną autoryzacją środków dla tego samego przejazdu.
     */
    @Transactional
    public Payment authorize(Ride ride) {
        /*
         * Idempotencja na poziomie domenowym.
         * Jeżeli płatność dla ride już istnieje, nie wykonujemy kolejnej autoryzacji.
         */
        var existing = payments.findByRideId(ride.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        /*
         * Klucz idempotencji dla providera płatniczego.
         * Dzięki temu retry po stronie providera nie powinien założyć drugiej blokady środków.
         */
        var idempotencyKey = "authorize:" + ride.getId();

        /*
         * Wywołanie providera płatności.
         * W MVP provider zwykle zwraca sukces, ale produkcyjnie może tu wystąpić timeout,
         * odmowa karty albo błąd systemu płatniczego.
         */
        var result = provider.authorize(
                idempotencyKey,
                ride.getEstimatedPrice(),
                ride.getCurrency()
        );

        var now = Instant.now();

        /*
         * Tworzymy trwały rekord płatności niezależnie od sukcesu.
         * Przy nieudanej autoryzacji zapis statusu jest równie ważny,
         * bo RideService może później zareagować na AUTHORIZATION_FAILED.
         */
        var payment = Payment.builder()
                .id(UUID.randomUUID())
                .ride(ride)
                .passenger(ride.getPassenger())
                .amount(ride.getEstimatedPrice())
                .currency(ride.getCurrency())

                /*
                 * MVP: provider jest zapisany jako "mock".
                 * Produkcyjnie ta wartość powinna odpowiadać realnemu providerowi,
                 * np. stripe, adyen, przelewy24.
                 */
                .provider("mock")

                .providerPaymentId(result.providerPaymentId())
                .idempotencyKey(idempotencyKey)
                .status(result.success()
                        ? PaymentStatus.AUTHORIZED
                        : PaymentStatus.AUTHORIZATION_FAILED)
                .authorizedAt(result.success() ? now : null)
                .failedAt(result.success() ? null : now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        var saved = payments.save(payment);

        /*
         * Event płatniczy zapisany przez outbox.
         * Inne komponenty mogą później reagować na status płatności asynchronicznie.
         */
        outbox.paymentEvent(
                saved.getId(),
                result.success() ? "PaymentAuthorized" : "PaymentAuthorizationFailed",
                Map.of(
                        "paymentId", saved.getId().toString(),
                        "rideId", ride.getId().toString(),
                        "status", saved.getStatus().name(),
                        "providerMessage", result.message()
                )
        );

        return saved;
    }

    /**
     * Pobiera finalną płatność po zakończeniu przejazdu.
     *
     * Flow:
     * 1. Szuka istniejącej płatności dla ride.
     * 2. Jeżeli jej nie ma, próbuje ją najpierw autoryzować.
     * 3. Woła provider.capture() z providerPaymentId i finalną ceną.
     * 4. Aktualizuje amount i status na CAPTURED albo CAPTURE_FAILED.
     * 5. Ustawia capturedAt albo failedAt.
     * 6. Zapisuje event outbox PaymentCaptured / PaymentCaptureFailed.
     *
     * Capture powinien być wykonywany po zakończeniu kursu,
     * kiedy znana jest finalna kwota do pobrania.
     */
    @Transactional
    public Payment capture(Ride ride) {
        /*
         * Jeżeli z jakiegoś powodu authorize nie zostało wykonane wcześniej,
         * próbujemy utworzyć płatność teraz.
         *
         * To wygodny fallback dla MVP, ale produkcyjnie warto ostrożnie rozróżnić:
         * brak autoryzacji, nieudana autoryzacja i udana autoryzacja.
         */
        var payment = payments.findByRideId(ride.getId())
                .orElseGet(() -> authorize(ride));

        /*
         * Capture pobiera finalną cenę przejazdu.
         * W tym projekcie finalPrice zwykle jest ustawione w RideService.completeRide().
         */
        var result = provider.capture(
                payment.getProviderPaymentId(),
                ride.getFinalPrice(),
                ride.getCurrency()
        );

        /*
         * Po capture aktualizujemy kwotę na finalną.
         * Może się różnić od estimate, gdy system zacznie uwzględniać realny dystans/czas.
         */
        payment.setAmount(ride.getFinalPrice());
        payment.setStatus(result.success()
                ? PaymentStatus.CAPTURED
                : PaymentStatus.CAPTURE_FAILED);

        if (result.success()) {
            payment.setCapturedAt(Instant.now());
        } else {
            payment.setFailedAt(Instant.now());
        }

        var saved = payments.save(payment);

        /*
         * Event po próbie capture.
         * Przy sukcesie może uruchomić paragon/fakturę i rozliczenie kierowcy.
         * Przy błędzie może uruchomić retry, reconciliation albo ticket supportowy.
         */
        outbox.paymentEvent(
                saved.getId(),
                result.success() ? "PaymentCaptured" : "PaymentCaptureFailed",
                Map.of(
                        "paymentId", saved.getId().toString(),
                        "rideId", ride.getId().toString(),
                        "status", saved.getStatus().name(),
                        "providerMessage", result.message()
                )
        );

        return saved;
    }
}