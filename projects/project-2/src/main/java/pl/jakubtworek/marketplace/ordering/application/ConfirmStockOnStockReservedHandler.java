package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.domain.StockReserved;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

/**
 * Handler reagujący na zdarzenie StockReserved.
 *
 * Ta klasa należy do modułu Ordering, mimo że obsługuje zdarzenie pochodzące
 * z modułu Inventory. To Ordering jest właścicielem procesu zamówienia, więc to on
 * decyduje, co oznacza fakt zarezerwowania stocku dla zamówienia.
 *
 * Przepływ:
 * - moduł Inventory publikuje fakt biznesowy StockReserved,
 * - moduł Ordering reaguje na ten fakt,
 * - agregat Order oznacza, że stock został zarezerwowany,
 * - jeśli płatność również jest już zarezerwowana, Order może przejść do CONFIRMED
 *   i wygenerować zdarzenie OrderConfirmed.
 *
 * Dzięki temu Inventory nie musi znać procesu zamówienia ani statusów Order.
 */
@Component
public class ConfirmStockOnStockReservedHandler implements DomainEventHandler<StockReserved> {

    /**
     * Port repozytorium zamówień.
     *
     * Handler odczytuje i zapisuje agregat Order, ale zależy tylko od abstrakcji.
     * Szczegóły zapisu, np. in-memory, JDBC albo JPA, pozostają w infrastrukturze.
     */
    private final OrderRepository repository;

    /**
     * Port publikowania zdarzeń.
     *
     * Po zmianie agregatu Order mogą powstać kolejne zdarzenia domenowe,
     * np. OrderConfirmed. EventPublisher odpowiada za ich publikację lub zapis do outboxa.
     */
    private final EventPublisher eventPublisher;

    public ConfirmStockOnStockReservedHandler(
            OrderRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Informuje mechanizm dispatchowania, że ten handler obsługuje zdarzenia StockReserved.
     */
    @Override
    public Class<StockReserved> eventType() {
        return StockReserved.class;
    }

    /**
     * Obsługuje zdarzenie StockReserved.
     *
     * Cała reakcja jest transakcyjna:
     * - odczyt zamówienia,
     * - zmiana stanu agregatu Order,
     * - zapis agregatu,
     * - zapis/publikacja nowych zdarzeń.
     *
     * event.orderId() wskazuje zamówienie, dla którego zarezerwowano stock.
     * event.correlationId() pozwala śledzić cały flow zamówienia.
     * event.eventId() jest przekazywany jako causationId, czyli informacja,
     * że zmiana w Order powstała jako skutek konkretnego StockReserved.
     */
    @Override
    @Transactional
    public void handle(StockReserved event) {
        var order = repository.findById(OrderId.of(event.orderId()))
                .orElseThrow();

        /*
         * Operacja domenowa na agregacie Order.
         *
         * To agregat Order decyduje, czy sama rezerwacja stocku wystarczy do zmiany statusu,
         * czy trzeba jeszcze czekać na płatność. Jeśli płatność jest już potwierdzona,
         * ta metoda może doprowadzić do wygenerowania OrderConfirmed.
         */
        order.markStockReserved(
                event.correlationId(),
                event.eventId()
        );

        /*
         * Zapisujemy zmieniony agregat Order.
         *
         * Przy trwałym outboxie zapis agregatu i zapis eventów powinny wykonać się
         * w tej samej transakcji.
         */
        repository.save(order);

        /*
         * Kopiujemy eventy przed publikacją.
         *
         * To chroni przed ponowną publikacją tych samych zdarzeń, jeśli ten sam obiekt
         * agregatu byłby jeszcze używany po zakończeniu operacji.
         */
        var events = java.util.List.copyOf(order.domainEvents());

        /*
         * Czyścimy eventy z agregatu po ich skopiowaniu.
         */
        order.clearDomainEvents();

        /*
         * Publikujemy eventy przez port.
         *
         * W docelowej wersji oznacza to zapis do outboxa, a następnie publikację do Kafki
         * przez worker outboxa.
         */
        events.forEach(eventPublisher::publish);
    }
}