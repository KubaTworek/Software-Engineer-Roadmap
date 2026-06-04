package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.payment.domain.PaymentReserved;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

/**
 * Handler reagujący na zdarzenie PaymentReserved.
 *
 * Ta klasa należy do modułu Ordering, mimo że obsługuje zdarzenie pochodzące
 * z modułu Payment. Jest to poprawne, ponieważ to Ordering jest właścicielem
 * procesu zamówienia i statusu zamówienia.
 *
 * Przepływ:
 * - moduł Payment publikuje fakt biznesowy PaymentReserved,
 * - moduł Ordering reaguje na ten fakt,
 * - agregat Order oznacza, że płatność została zarezerwowana,
 * - jeśli pozostałe warunki są spełnione, Order może wygenerować kolejne zdarzenie,
 *   np. OrderConfirmed.
 *
 * Dzięki temu Payment nie musi znać szczegółów procesu zamówienia.
 */
@Component
public class ConfirmPaymentOnPaymentReservedHandler implements DomainEventHandler<PaymentReserved> {

    /**
     * Port repozytorium zamówień.
     *
     * Handler potrzebuje odczytać i zapisać agregat Order, ale nie powinien znać
     * szczegółów implementacji repozytorium, np. JDBC, JPA albo in-memory.
     */
    private final OrderRepository repository;

    /**
     * Port publikowania zdarzeń.
     *
     * Po zmianie stanu agregatu Order mogą powstać nowe zdarzenia domenowe.
     * W obecnej architekturze EventPublisher najczęściej zapisuje je do outboxa,
     * a nie publikuje bezpośrednio do kolejnych handlerów.
     */
    private final EventPublisher eventPublisher;

    public ConfirmPaymentOnPaymentReservedHandler(
            OrderRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Informuje mechanizm dispatchowania, że ten handler obsługuje PaymentReserved.
     */
    @Override
    public Class<PaymentReserved> eventType() {
        return PaymentReserved.class;
    }

    /**
     * Obsługuje zdarzenie PaymentReserved.
     *
     * Granica transakcji znajduje się na poziomie handlera, ponieważ cała reakcja
     * na zdarzenie powinna być atomowa:
     * - odczyt zamówienia,
     * - zmiana stanu agregatu Order,
     * - zapis agregatu,
     * - zapis nowo powstałych zdarzeń do outboxa.
     *
     * event.orderId() wskazuje, którego zamówienia dotyczy zarezerwowana płatność.
     * event.correlationId() pozwala śledzić cały flow zamówienia.
     * event.eventId() jest przekazywany jako causationId, czyli informacja:
     * "ta zmiana w Order powstała jako skutek konkretnego PaymentReserved".
     */
    @Override
    @Transactional
    public void handle(PaymentReserved event) {
        var order = repository.findById(OrderId.of(event.orderId()))
                .orElseThrow();

        /*
         * Operacja domenowa na agregacie Order.
         *
         * To Order decyduje, co oznacza potwierdzenie rezerwacji płatności.
         * Może tylko zapamiętać fakt rezerwacji albo, jeśli stock też jest już
         * zarezerwowany, przejść do statusu CONFIRMED i wygenerować OrderConfirmed.
         */
        order.markPaymentReserved(
                event.correlationId(),
                event.eventId()
        );

        /*
         * Zapisujemy zmieniony agregat.
         *
         * Przy adapterze JDBC/JPA zapis Order oraz zapis eventów do outboxa powinny
         * wykonać się w ramach tej samej transakcji.
         */
        repository.save(order);

        /*
         * Kopiujemy eventy przed publikacją.
         *
         * To chroni przed przypadkowym ponownym opublikowaniem tych samych eventów,
         * szczególnie w przepływach, gdzie publikacja jednego eventu może wywołać
         * kolejne handlery.
         */
        var events = java.util.List.copyOf(order.domainEvents());

        /*
         * Czyścimy eventy z agregatu po ich skopiowaniu.
         *
         * Agregat nie powinien nosić w sobie już opublikowanych zdarzeń.
         */
        order.clearDomainEvents();

        /*
         * Publikujemy nowo powstałe zdarzenia przez port EventPublisher.
         *
         * W fazie outboxa oznacza to zwykle zapis do tabeli integration.outbox_events.
         */
        events.forEach(eventPublisher::publish);
    }
}