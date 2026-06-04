package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.domain.StockReservationFailed;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

/**
 * Handler reagujący na zdarzenie StockReservationFailed.
 *
 * Ta klasa należy do modułu Ordering, mimo że obsługiwane zdarzenie pochodzi
 * z modułu Inventory. To Ordering jest właścicielem procesu zamówienia, więc to on
 * decyduje, co zrobić z zamówieniem, gdy nie udało się zarezerwować stocku.
 *
 * Przepływ:
 * - moduł Inventory próbuje zarezerwować stock po zdarzeniu OrderPlaced,
 * - jeśli stocku brakuje albo produkt nie ma wpisu magazynowego, publikuje StockReservationFailed,
 * - moduł Ordering odbiera ten fakt,
 * - agregat Order zostaje oznaczony jako odrzucony.
 *
 * Dzięki temu Inventory nie musi znać statusów zamówienia ani logiki procesu order fulfillment.
 */
@Component
public class RejectOrderOnStockReservationFailedHandler implements DomainEventHandler<StockReservationFailed> {

    /**
     * Port repozytorium zamówień.
     *
     * Handler odczytuje i zapisuje agregat Order, ale zależy tylko od abstrakcji.
     * Konkretna implementacja repozytorium, np. in-memory, JDBC albo JPA,
     * znajduje się w warstwie infrastruktury.
     */
    private final OrderRepository repository;

    public RejectOrderOnStockReservationFailedHandler(OrderRepository repository) {
        this.repository = repository;
    }

    /**
     * Informuje mechanizm dispatchowania, że ten handler obsługuje zdarzenia
     * StockReservationFailed.
     */
    @Override
    public Class<StockReservationFailed> eventType() {
        return StockReservationFailed.class;
    }

    /**
     * Obsługuje zdarzenie StockReservationFailed.
     *
     * Cała reakcja jest transakcyjna:
     * - odczyt zamówienia,
     * - zmiana stanu agregatu Order,
     * - zapis agregatu.
     *
     * event.orderId() wskazuje zamówienie, dla którego nie udało się zarezerwować stocku.
     * event.reason() opisuje przyczynę porażki, np. brak produktu w magazynie
     * albo niewystarczającą ilość.
     */
    @Override
    @Transactional
    public void handle(StockReservationFailed event) {
        var order = repository.findById(OrderId.of(event.orderId()))
                .orElseThrow();

        /*
         * Operacja domenowa na agregacie Order.
         *
         * To agregat decyduje, czy w aktualnym stanie można oznaczyć zamówienie
         * jako odrzucone. Handler nie powinien samodzielnie ustawiać statusu.
         */
        order.reject(event.reason());

        /*
         * Zapisujemy zmieniony agregat.
         *
         * Przy trwałej implementacji repozytorium zapis powinien wykonać się w tej samej
         * transakcji co obsługa eventu przez konsumenta.
         */
        repository.save(order);

        /*
         * Czyścimy ewentualne zdarzenia domenowe z agregatu.
         *
         * Uwaga: jeśli Order.reject(...) zacznie w przyszłości rejestrować np. OrderRejected,
         * to samo clearDomainEvents() spowoduje utratę tego eventu. Wtedy handler powinien
         * publikować zdarzenia przez EventPublisher, tak jak handlery potwierdzające płatność
         * albo stock.
         */
        order.clearDomainEvents();
    }
}