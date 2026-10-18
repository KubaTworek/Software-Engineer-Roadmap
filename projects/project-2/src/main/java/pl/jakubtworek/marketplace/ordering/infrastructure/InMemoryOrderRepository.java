package pl.jakubtworek.marketplace.ordering.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.ordering.application.OrderRepository;
import pl.jakubtworek.marketplace.ordering.application.ConcurrentOrderModificationException;
import pl.jakubtworek.marketplace.ordering.domain.Order;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementacja portu OrderRepository.
 *
 * Ta klasa należy do warstwy infrastruktury modułu Ordering.
 * Implementuje port z warstwy aplikacyjnej, ale nie powinna być znana domenie.
 *
 * Jest to prosta implementacja przydatna na początkowym etapie projektu:
 * - pozwala rozwijać domenę bez bazy danych,
 * - upraszcza testy jednostkowe i komponentowe,
 * - pozwala szybko uruchomić aplikację lokalnie.
 *
 * Nie jest to implementacja produkcyjna.
 * Dane są trzymane tylko w pamięci procesu i znikają po restarcie aplikacji.
 */
@Repository
@Profile("!postgres")
public class InMemoryOrderRepository implements OrderRepository {

    /**
     * Prosty magazyn zamówień w pamięci.
     *
     * Kluczem jest domenowy OrderId, a wartością agregat Order.
     *
     * ConcurrentHashMap chroni samą strukturę mapy przed podstawowymi problemami
     * współbieżności, ale nie zapewnia pełnej transakcyjności ani izolacji takiej jak baza danych.
     */
    private final Map<OrderId, Order> orders = new ConcurrentHashMap<>();

    /**
     * Zapisuje agregat Order w pamięci.
     *
     * Jeśli zamówienie o tym samym ID już istnieje, zostanie nadpisane.
     *
     * Adapter celowo zapisuje kopię agregatu. Przechowywanie tej samej referencji
     * sprawiałoby, że zmiana byłaby widoczna bez save(), a test udawałby semantykę,
     * której PostgreSQL nie zapewnia. Wersja jest sprawdzana atomowo pod blokadą
     * repozytorium, aby fake również wykrywał lost update.
     */
    @Override
    public synchronized Order save(Order order) {
        Order current = orders.get(order.id());
        if (current == null) {
            if (order.version() >= 0) {
                throw new ConcurrentOrderModificationException(order.id(), order.version());
            }
            order.markPersisted(0L);
        } else {
            if (current.version() != order.version()) {
                throw new ConcurrentOrderModificationException(order.id(), order.version());
            }
            order.markPersisted(order.version() + 1);
        }
        orders.put(order.id(), snapshot(order));
        return order;
    }

    /**
     * Wyszukuje zamówienie po identyfikatorze.
     *
     * Zwracamy Optional, ponieważ zamówienie o podanym ID może nie istnieć.
     * Warstwa aplikacyjna albo API powinna jawnie obsłużyć taki przypadek,
     * najlepiej przez dedykowany wyjątek mapowany później na HTTP 404.
     */
    @Override
    public Optional<Order> findById(OrderId id) {
        return Optional.ofNullable(orders.get(id)).map(this::snapshot);
    }

    private Order snapshot(Order order) {
        return Order.restore(order.id(), order.customerId(), order.lines(), order.status(),
                order.paymentReserved(), order.stockReserved(), order.version());
    }
}
