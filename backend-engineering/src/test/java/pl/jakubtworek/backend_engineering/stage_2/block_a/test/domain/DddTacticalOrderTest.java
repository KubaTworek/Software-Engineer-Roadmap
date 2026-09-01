package pl.jakubtworek.backend_engineering.stage_2.block_a.test.domain;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.event.OrderPaid;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.event.OrderPlaced;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.CustomerId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.Money;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.OrderId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.OrderStatus;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.ProductId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.Quantity;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DddTacticalOrderTest {

    private static final Currency PLN = Currency.getInstance("PLN");

    @Test
    void shouldProtectCurrencyInvariantWhenAddingLines() {
        Order order = draft();
        Money euroPrice = Money.of(new BigDecimal("10.00"), Currency.getInstance("EUR"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> order.addLine(ProductId.of("P-1"), Quantity.of(1), euroPrice)
        );

        assertEquals("Order line currency must match order currency", exception.getMessage());
        assertTrue(order.lines().isEmpty());
        assertEquals(Money.zero(PLN), order.totalPrice());
    }

    @Test
    void shouldPlaceNonEmptyOrderAndExposeEventOnlyOnce() {
        Order order = draftWithOneLine();

        order.place();

        assertEquals(OrderStatus.PLACED, order.status());
        assertInstanceOf(OrderPlaced.class, order.pullDomainEvents().get(0));
        assertTrue(order.pullDomainEvents().isEmpty());
        assertThrows(IllegalStateException.class, order::place);
    }

    @Test
    void shouldRejectEmptyOrder() {
        Order order = draft();

        assertThrows(IllegalStateException.class, order::place);
        assertEquals(OrderStatus.DRAFT, order.status());
        assertTrue(order.pullDomainEvents().isEmpty());
    }

    @Test
    void shouldRejectBlankPaymentIdWithoutChangingPlacedOrder() {
        Order order = draftWithOneLine();
        order.place();
        order.pullDomainEvents();

        assertThrows(IllegalArgumentException.class, () -> order.markAsPaid(" "));
        assertEquals(OrderStatus.PLACED, order.status());
        assertTrue(order.pullDomainEvents().isEmpty());
    }

    @Test
    void shouldMarkPlacedOrderAsPaidAndEmitEvent() {
        Order order = draftWithOneLine();
        order.place();
        order.pullDomainEvents();

        order.markAsPaid("PAY-1");

        assertEquals(OrderStatus.PAID, order.status());
        OrderPaid event = assertInstanceOf(OrderPaid.class, order.pullDomainEvents().get(0));
        assertEquals("PAY-1", event.paymentId());
        assertEquals(order.id(), event.orderId());
    }

    @Test
    void moneyShouldUseNumericEqualityInsteadOfBigDecimalScale() {
        Money first = Money.of(new BigDecimal("10.0"), PLN);
        Money second = Money.of(new BigDecimal("10.00"), PLN);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    private Order draftWithOneLine() {
        Order order = draft();
        order.addLine(
                ProductId.of("P-1"),
                Quantity.of(2),
                Money.of(new BigDecimal("12.50"), PLN)
        );
        return order;
    }

    private Order draft() {
        return Order.draft(OrderId.of("O-1"), CustomerId.of("C-1"), PLN);
    }
}
