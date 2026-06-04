package pl.jakubtworek.marketplace.shared.kernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bazowa klasa dla agregatów domenowych.
 *
 * AggregateRoot przechowuje zdarzenia domenowe wygenerowane przez agregat
 * podczas wykonywania operacji biznesowych.
 *
 * Przykład:
 * - Order po złożeniu zamówienia rejestruje OrderPlaced,
 * - Order po anulowaniu rejestruje OrderCancelled,
 * - Payment po rezerwacji płatności rejestruje PaymentReserved,
 * - StockItem po rezerwacji stocku może rejestrować StockReserved.
 *
 * Ta klasa należy do shared kernel, ponieważ mechanizm zbierania zdarzeń
 * jest wspólny dla wielu modułów domenowych.
 *
 * Ważne:
 * - AggregateRoot nie publikuje zdarzeń samodzielnie,
 * - agregat tylko rejestruje fakty domenowe,
 * - warstwa aplikacyjna pobiera zdarzenia i przekazuje je do EventPublisher,
 * - EventPublisher może zapisać zdarzenia do outboxa albo przekazać je dalej.
 */
public abstract class AggregateRoot {

    /**
     * Lista zdarzeń domenowych wygenerowanych przez agregat.
     *
     * Lista jest prywatna, żeby kod z zewnątrz nie mógł jej dowolnie modyfikować.
     * Zdarzenia powinny być dodawane wyłącznie przez metody domenowe agregatu
     * za pomocą registerEvent(...).
     */
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * Rejestruje zdarzenie domenowe w agregacie.
     *
     * Metoda jest protected, ponieważ powinna być używana tylko wewnątrz agregatu
     * albo jego klas potomnych. Kod aplikacyjny nie powinien dopisywać eventów
     * do agregatu z zewnątrz.
     *
     * Zdarzenie powinno reprezentować fakt, który już się wydarzył w domenie,
     * np. "zamówienie zostało złożone", a nie komendę typu "złóż zamówienie".
     */
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    /**
     * Zwraca zdarzenia domenowe zgromadzone w agregacie.
     *
     * Zwracamy niemodyfikowalny widok listy, żeby kod z zewnątrz mógł odczytać eventy,
     * ale nie mógł ich przypadkowo dodać, usunąć albo zmienić.
     *
     * Typowy schemat użycia w use case:
     *
     * var events = List.copyOf(order.domainEvents());
     * order.clearDomainEvents();
     * events.forEach(eventPublisher::publish);
     */
    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Czyści listę zdarzeń domenowych po ich przekazaniu do publikacji.
     *
     * To ważne, ponieważ ten sam obiekt agregatu może być jeszcze używany po zapisie.
     * Bez czyszczenia moglibyśmy przypadkowo opublikować te same eventy drugi raz.
     *
     * Najbezpieczniejszy wzorzec:
     * 1. skopiować eventy,
     * 2. wyczyścić eventy z agregatu,
     * 3. opublikować skopiowane eventy.
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }
}