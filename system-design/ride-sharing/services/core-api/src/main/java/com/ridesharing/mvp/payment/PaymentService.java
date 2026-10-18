package com.ridesharing.mvp.payment;

import com.ridesharing.mvp.ride.Ride;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

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
    private final PaymentPersistenceService persistence;

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
    public Payment authorize(Ride ride) {
        /*
         * Idempotencja na poziomie domenowym.
         * Jeżeli płatność dla ride już istnieje, nie wykonujemy kolejnej autoryzacji.
         */
        var existing = persistence.find(ride.getId());
        if (existing != null) {
            return existing;
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

        try {
            return persistence.recordAuthorization(ride, idempotencyKey, result);
        } catch (DataIntegrityViolationException concurrentAuthorization) {
            var winner = persistence.find(ride.getId());
            if (winner != null) {
                return winner;
            }
            throw concurrentAuthorization;
        }
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
    public Payment capture(Ride ride) {
        /*
         * Jeżeli z jakiegoś powodu authorize nie zostało wykonane wcześniej,
         * próbujemy utworzyć płatność teraz.
         *
         * To wygodny fallback dla MVP, ale produkcyjnie warto ostrożnie rozróżnić:
         * brak autoryzacji, nieudana autoryzacja i udana autoryzacja.
         */
        var payment = persistence.find(ride.getId());
        if (payment == null) {
            payment = authorize(ride);
        }
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return payment;
        }

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
        return persistence.recordCapture(payment.getId(), ride, result);
    }
}
