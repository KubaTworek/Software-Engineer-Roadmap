package pl.jakubtworek.backend_engineering.stage_2.block_a.test.domain;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.DomainEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.OrderPlacedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.*;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for the Order aggregate.
// These tests verify business behavior without database, Spring, HTTP, or messaging infrastructure.
class OrderTest {

    @Test
    void shouldPlaceOrderWhenTotalMatchesAndOrderHasLines() {
        // Given
        Currency currency = Currency.getInstance("PLN");

        Order order = Order.create(
                OrderId.of("O-123"),
                CustomerId.of("C-456"),
                currency
        );

        order.addLine(
                ProductId.of("P-1"),
                2,
                Money.of(new BigDecimal("50.00"), currency)
        );

        // When
        order.place(Money.of(new BigDecimal("100.00"), currency));

        // Then
        assertEquals(OrderStatus.PLACED, order.status());
        assertEquals(Money.of(new BigDecimal("100.00"), currency), order.total());

        List<DomainEvent> events = order.uncommittedEvents();

        assertEquals(1, events.size());
        assertInstanceOf(OrderPlacedEvent.class, events.get(0));

        OrderPlacedEvent event = (OrderPlacedEvent) events.get(0);

        assertEquals("O-123", event.orderId().value());
        assertEquals("C-456", event.customerId().value());
        assertEquals(Money.of(new BigDecimal("100.00"), currency), event.total());
    }

    @Test
    void shouldRejectOrderWithoutLines() {
        // Given
        Currency currency = Currency.getInstance("PLN");

        Order order = Order.create(
                OrderId.of("O-123"),
                CustomerId.of("C-456"),
                currency
        );

        // When / Then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> order.place(Money.of(new BigDecimal("0.00"), currency))
        );

        assertEquals("Order must contain at least one line", exception.getMessage());
        assertTrue(order.uncommittedEvents().isEmpty());
    }

    @Test
    void shouldRejectOrderWhenExpectedTotalDoesNotMatchCalculatedTotal() {
        // Given
        Currency currency = Currency.getInstance("PLN");

        Order order = Order.create(
                OrderId.of("O-123"),
                CustomerId.of("C-456"),
                currency
        );

        order.addLine(
                ProductId.of("P-1"),
                2,
                Money.of(new BigDecimal("50.00"), currency)
        );

        // When / Then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> order.place(Money.of(new BigDecimal("90.00"), currency))
        );

        assertEquals("Expected total does not match calculated total", exception.getMessage());
        assertEquals(OrderStatus.DRAFT, order.status());
        assertTrue(order.uncommittedEvents().isEmpty());
    }

    @Test
    void shouldNotAllowModifyingPlacedOrder() {
        // Given
        Currency currency = Currency.getInstance("PLN");

        Order order = Order.create(
                OrderId.of("O-123"),
                CustomerId.of("C-456"),
                currency
        );

        order.addLine(
                ProductId.of("P-1"),
                1,
                Money.of(new BigDecimal("50.00"), currency)
        );

        order.place(Money.of(new BigDecimal("50.00"), currency));

        // When / Then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> order.addLine(
                        ProductId.of("P-2"),
                        1,
                        Money.of(new BigDecimal("20.00"), currency)
                )
        );

        assertEquals("Only draft order can be modified", exception.getMessage());
    }
}