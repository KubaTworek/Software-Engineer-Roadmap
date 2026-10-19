package pl.jakubtworek.marketplace.ordering.infrastructure;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.ordering.application.ConcurrentOrderModificationException;
import pl.jakubtworek.marketplace.ordering.domain.CustomerId;
import pl.jakubtworek.marketplace.ordering.domain.Order;
import pl.jakubtworek.marketplace.ordering.domain.OrderLine;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kontrprzykład dla klasycznego lost update w przepływie zdarzeniowym.
 * Payment i Inventory mogą zakończyć się niemal równocześnie. Jeśli oba handlery
 * zapiszą wersję 0 przez zwykły UPSERT, jedna flaga zniknie i Order nigdy nie
 * przejdzie do CONFIRMED. Optimistic locking zmusza przegrywający handler do
 * ponownego odczytu wersji 1 i bezpiecznego powtórzenia operacji.
 */
class OrderOptimisticLockingTest {

    @Test
    void rejectsSecondWriterThatLoadedTheSameAggregateVersion() {
        var repository = new InMemoryOrderRepository();
        var order = Order.place(CustomerId.of(UUID.randomUUID()), List.of(new OrderLine(
                UUID.randomUUID(), 1, Money.of(new BigDecimal("10.00"), Currency.getInstance("PLN")))), UUID.randomUUID());
        repository.save(order);

        Order paymentHandlerCopy = repository.findById(order.id()).orElseThrow();
        Order stockHandlerCopy = repository.findById(order.id()).orElseThrow();
        paymentHandlerCopy.markPaymentReserved(UUID.randomUUID(), UUID.randomUUID());
        stockHandlerCopy.markStockReserved(UUID.randomUUID(), UUID.randomUUID());

        repository.save(paymentHandlerCopy);

        assertThatThrownBy(() -> repository.save(stockHandlerCopy))
                .isInstanceOf(ConcurrentOrderModificationException.class);
        assertThat(repository.findById(order.id()).orElseThrow().paymentReserved()).isTrue();
        assertThat(repository.findById(order.id()).orElseThrow().stockReserved()).isFalse();
    }
}
