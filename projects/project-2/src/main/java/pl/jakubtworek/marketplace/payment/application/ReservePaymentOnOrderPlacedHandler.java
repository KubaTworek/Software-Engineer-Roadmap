package pl.jakubtworek.marketplace.payment.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.payment.domain.Payment;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

/**
 * Handler reagujący na zdarzenie OrderPlaced.
 *
 * Ta klasa należy do modułu Payment. Jej zadaniem jest rozpoczęcie procesu
 * rezerwacji płatności po złożeniu zamówienia.
 *
 * Ważna zasada architektoniczna:
 * - moduł Ordering nie wywołuje bezpośrednio Payment,
 * - Ordering publikuje fakt biznesowy OrderPlaced,
 * - Payment reaguje na ten fakt i sam decyduje, jak obsłużyć płatność.
 *
 * Dzięki temu moduły są luźniej powiązane, a komunikacja odbywa się przez zdarzenia.
 */
@Component
public class ReservePaymentOnOrderPlacedHandler implements DomainEventHandler<OrderPlaced> {

    /**
     * Port do zewnętrznej bramki płatności.
     *
     * Handler nie zna konkretnego dostawcy płatności. Zna tylko abstrakcję PaymentGateway.
     * Implementacja może być:
     * - fake’owa w testach,
     * - symulowana lokalnie,
     * - prawdziwa w środowisku produkcyjnym.
     */
    private final PaymentGateway gateway;

    /**
     * Port repozytorium płatności.
     *
     * Handler zapisuje agregat Payment, ale nie zna szczegółów zapisu,
     * np. JDBC, JPA albo implementacji in-memory.
     */
    private final PaymentRepository repository;

    /**
     * Port publikowania zdarzeń.
     *
     * Agregat Payment może wygenerować zdarzenia takie jak PaymentReserved
     * albo PaymentRejected. EventPublisher odpowiada za przekazanie ich dalej,
     * najczęściej przez zapis do outboxa.
     */
    private final EventPublisher eventPublisher;

    public ReservePaymentOnOrderPlacedHandler(
            PaymentGateway gateway,
            PaymentRepository repository,
            EventPublisher eventPublisher
    ) {
        this.gateway = gateway;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Informuje mechanizm dispatchowania, że ten handler obsługuje zdarzenie OrderPlaced.
     */
    @Override
    public Class<OrderPlaced> eventType() {
        return OrderPlaced.class;
    }

    /**
     * Obsługuje zdarzenie OrderPlaced przez próbę rezerwacji płatności.
     *
     * Cała reakcja jest transakcyjna:
     * - wywołanie portu PaymentGateway,
     * - utworzenie agregatu Payment,
     * - zapis Payment,
     * - publikacja zdarzeń wygenerowanych przez Payment.
     *
     * event.aggregateId() oznacza identyfikator zamówienia.
     * event.total() oznacza kwotę zamówienia do zarezerwowania.
     * event.correlationId() pozwala śledzić cały flow zamówienia.
     * event.eventId() jest używany jako causationId, czyli informacja,
     * że PaymentReserved/PaymentRejected powstało jako skutek konkretnego OrderPlaced.
     */
    @Override
    @Transactional
    public void handle(OrderPlaced event) {
        /*
         * Próba rezerwacji płatności przez port.
         *
         * Handler nie wie, czy pod spodem jest fake gateway, zewnętrzne API,
         * czy symulowana implementacja. To szczegół infrastruktury.
         */
        var result = gateway.reserve(
                event.aggregateId(),
                event.total()
        );

        /*
         * Utworzenie agregatu Payment na podstawie wyniku rezerwacji.
         *
         * To Payment.reserve(...) decyduje, jaki status będzie miała płatność
         * i jakie zdarzenie zostanie zarejestrowane:
         * - PaymentReserved, jeśli gateway zaakceptował rezerwację,
         * - PaymentRejected, jeśli gateway ją odrzucił.
         */
        var payment = Payment.reserve(
                event.aggregateId(),
                event.total(),
                result.accepted(),
                event.correlationId(),
                event.eventId()
        );

        /*
         * Zapis agregatu Payment.
         *
         * Przy trwałym outboxie zapis płatności i zapis zdarzenia do outboxa powinny
         * wydarzyć się w tej samej transakcji.
         */
        repository.save(payment);

        /*
         * Publikujemy zdarzenia wygenerowane przez agregat Payment.
         *
         * Uwaga: lepiej najpierw skopiować eventy, potem je wyczyścić, a dopiero potem
         * publikować. Obecny wariant działa, ale jest mniej bezpieczny, jeśli publisher
         * albo handler uruchomi bardziej złożony flow.
         */
        payment.domainEvents().forEach(eventPublisher::publish);

        /*
         * Czyścimy eventy z agregatu po publikacji.
         */
        payment.clearDomainEvents();
    }
}