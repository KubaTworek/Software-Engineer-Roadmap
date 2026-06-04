package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.payment.domain.PaymentRejected;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

/**
 * Handler reagujący na zdarzenie PaymentRejected.
 *
 * Ta klasa należy do modułu Ordering, mimo że obsługiwane zdarzenie pochodzi
 * z modułu Payment. To Ordering jest właścicielem cyklu życia zamówienia,
 * więc to on decyduje, co zrobić z zamówieniem, gdy płatność zostanie odrzucona.
 *
 * Przepływ:
 * - moduł Payment publikuje fakt biznesowy PaymentRejected,
 * - moduł Ordering odbiera ten fakt,
 * - agregat Order zostaje oznaczony jako odrzucony,
 * - zmieniony stan zamówienia zostaje zapisany.
 *
 * Dzięki temu Payment nie musi znać statusów ani reguł procesu zamówienia.
 */
@Component
public class RejectOrderOnPaymentRejectedHandler implements DomainEventHandler<PaymentRejected> {

    /**
     * Port repozytorium zamówień.
     *
     * Handler potrzebuje odczytać i zapisać agregat Order, ale zależy tylko od abstrakcji.
     * Szczegóły zapisu, np. in-memory, JDBC albo JPA, są ukryte w warstwie infrastruktury.
     */
    private final OrderRepository repository;

    public RejectOrderOnPaymentRejectedHandler(OrderRepository repository) {
        this.repository = repository;
    }

    /**
     * Informuje mechanizm dispatchowania, że ten handler obsługuje zdarzenia PaymentRejected.
     */
    @Override
    public Class<PaymentRejected> eventType() {
        return PaymentRejected.class;
    }

    /**
     * Obsługuje zdarzenie PaymentRejected.
     *
     * Cała reakcja jest transakcyjna:
     * - odczyt zamówienia,
     * - zmiana stanu agregatu Order,
     * - zapis agregatu.
     *
     * event.orderId() wskazuje zamówienie, którego dotyczy odrzucona płatność.
     * event.reason() przenosi powód odrzucenia płatności, który może zostać zapisany
     * w domenie albo użyty do późniejszej diagnostyki.
     */
    @Override
    @Transactional
    public void handle(PaymentRejected event) {
        var order = repository.findById(OrderId.of(event.orderId()))
                .orElseThrow();

        /*
         * Operacja domenowa na agregacie Order.
         *
         * To Order powinien pilnować, czy w aktualnym stanie można przejść do statusu
         * odrzuconego. Handler nie powinien samodzielnie ustawiać statusu ani obchodzić
         * reguł domenowych.
         */
        order.reject(event.reason());

        /*
         * Zapisujemy zmieniony agregat.
         *
         * Przy adapterze JDBC/JPA zapis powinien wykonać się w ramach tej samej transakcji
         * co obsługa eventu przez konsumenta.
         */
        repository.save(order);

        /*
         * Czyścimy ewentualne zdarzenia domenowe z agregatu.
         *
         * W obecnej implementacji reject(...) prawdopodobnie nie publikuje kolejnego eventu.
         * Jeśli w przyszłości Order.reject(...) zacznie rejestrować np. OrderRejected,
         * to samo clearDomainEvents() będzie błędem, bo zdarzenie zostanie utracone.
         */
        order.clearDomainEvents();
    }
}