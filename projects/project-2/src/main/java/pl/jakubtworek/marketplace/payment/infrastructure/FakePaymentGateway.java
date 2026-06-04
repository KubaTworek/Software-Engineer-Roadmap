package pl.jakubtworek.marketplace.payment.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.payment.application.PaymentGateway;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.UUID;

/**
 * Fake'owa implementacja portu PaymentGateway.
 *
 * Ta klasa należy do warstwy infrastruktury modułu Payment.
 * Implementuje port z warstwy aplikacyjnej, ale nie reprezentuje prawdziwej integracji
 * z operatorem płatności.
 *
 * Jej cel:
 * - umożliwić lokalne uruchamianie systemu bez zewnętrznego dostawcy płatności,
 * - ułatwić testowanie flow zamówienia,
 * - pozwolić symulować zaakceptowaną albo odrzuconą płatność przez konfigurację.
 *
 * W produkcyjnej wersji ten adapter zostałby zastąpiony implementacją komunikującą się
 * z prawdziwym providerem płatności, np. Stripe, PayU, Adyen albo inną bramką.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    /**
     * Flaga konfiguracyjna decydująca, czy fake'owa bramka płatności akceptuje rezerwacje.
     *
     * Jeśli true:
     * - każda próba rezerwacji płatności zakończy się sukcesem.
     *
     * Jeśli false:
     * - każda próba rezerwacji płatności zostanie odrzucona.
     *
     * Wartość jest pobierana z konfiguracji:
     *
     * marketplace.payment.fake.accept-reservations=true
     */
    private final boolean acceptReservations;

    /**
     * Konstruktor używany przez Springa.
     *
     * @Value pozwala wstrzyknąć wartość z konfiguracji aplikacji.
     * Domyślnie, jeśli property nie zostanie ustawione, fake'owa bramka akceptuje płatności.
     */
    public FakePaymentGateway(
            @Value("${marketplace.payment.fake.accept-reservations:true}") boolean acceptReservations
    ) {
        this.acceptReservations = acceptReservations;
    }

    /**
     * Konstruktor pomocniczy.
     *
     * Ułatwia ręczne tworzenie FakePaymentGateway w testach jednostkowych bez uruchamiania
     * Spring Contextu. Domyślnie ustawia akceptowanie rezerwacji na true.
     */
    public FakePaymentGateway() {
        this(true);
    }

    /**
     * Symuluje rezerwację płatności.
     *
     * orderId identyfikuje zamówienie, dla którego próbujemy zarezerwować płatność.
     * amount zawiera kwotę i walutę rezerwacji.
     *
     * W tej implementacji parametry nie wpływają na decyzję. Wynik zależy wyłącznie od
     * konfiguracji acceptReservations.
     *
     * W prawdziwej implementacji tutaj znajdowałoby się np.:
     * - przygotowanie requestu do operatora płatności,
     * - wywołanie HTTP,
     * - obsługa timeoutów,
     * - mapowanie odpowiedzi zewnętrznego API na PaymentReservationResult.
     */
    @Override
    public PaymentReservationResult reserve(UUID orderId, Money amount) {
        if (acceptReservations) {
            return new PaymentReservationResult(
                    true,
                    "fake payment accepted"
            );
        }

        return new PaymentReservationResult(
                false,
                "fake payment rejected"
        );
    }
}