package pl.jakubtworek.marketplace.ordering.application;

import pl.jakubtworek.marketplace.ordering.domain.OrderId;

/**
 * Sygnalizuje, że odczytana wersja agregatu jest już nieaktualna.
 *
 * To błąd przejściowy: handler komunikatu może ponownie odczytać Order i powtórzyć
 * operację. Nie wolno jednak zamieniać go w bezwarunkowy, nieskończony retry — po
 * przekroczeniu budżetu zdarzenie powinno trafić do retry topicu albo DLQ.
 */
public class ConcurrentOrderModificationException extends RuntimeException {
    public ConcurrentOrderModificationException(OrderId orderId, long expectedVersion) {
        super("Order %s changed after version %d was read".formatted(orderId.value(), expectedVersion));
    }
}
