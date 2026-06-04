package pl.jakubtworek.marketplace.ordering.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.ordering.application.OrderRepository;
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
     * Warto pamiętać, że ta implementacja przechowuje referencję do obiektu.
     * To znaczy, że zmiany wykonane na pobranym Order mogą być widoczne w mapie
     * nawet bez ponownego wywołania save(...). Baza danych zwykle tak się nie zachowuje,
     * więc testy integracyjne dla adaptera JDBC/JPA są nadal potrzebne.
     */
    @Override
    public Order save(Order order) {
        orders.put(order.id(), order);
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
        return Optional.ofNullable(orders.get(id));
    }
}