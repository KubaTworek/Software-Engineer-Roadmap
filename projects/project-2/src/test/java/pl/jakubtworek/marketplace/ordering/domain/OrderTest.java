package pl.jakubtworek.marketplace.ordering.domain;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @Test
    void shouldPlaceOrderAndRegisterOrderPlacedEvent() {
        Order order = Order.place(CustomerId.of(UUID.randomUUID()), List.of(line("10.00", 2)), CORRELATION_ID);

        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.total()).isEqualTo(Money.of("20.00", "PLN"));
        assertThat(order.domainEvents()).hasSize(1);
        assertThat(order.domainEvents().getFirst()).isInstanceOf(OrderPlaced.class);

        OrderPlaced event = (OrderPlaced) order.domainEvents().getFirst();
        assertThat(event.aggregateId()).isEqualTo(order.id().value());
        assertThat(event.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(event.eventVersion()).isEqualTo(1);
    }

    @Test
    void shouldRejectOrderWithoutLines() {
        assertThatThrownBy(() -> Order.place(CustomerId.of(UUID.randomUUID()), List.of(), CORRELATION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order must have at least one line");
    }

    @Test
    void shouldRejectOrderLineWithNonPositiveQuantity() {
        assertThatThrownBy(() -> new OrderLine(ProductId.newId(), 0, Money.of("10.00", "PLN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");
    }

    @Test
    void shouldNotConfirmOrderWhenOnlyPaymentIsReserved() {
        Order order = Order.place(CustomerId.of(UUID.randomUUID()), List.of(line("10.00", 1)), CORRELATION_ID);
        order.clearDomainEvents();

        order.markPaymentReserved(CORRELATION_ID, UUID.randomUUID());

        assertThat(order.paymentReserved()).isTrue();
        assertThat(order.stockReserved()).isFalse();
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.domainEvents()).isEmpty();
    }

    @Test
    void shouldConfirmOrderWhenPaymentAndStockAreReserved() {
        Order order = Order.place(CustomerId.of(UUID.randomUUID()), List.of(line("10.00", 1)), CORRELATION_ID);
        order.clearDomainEvents();

        order.markPaymentReserved(CORRELATION_ID, UUID.randomUUID());
        order.markStockReserved(CORRELATION_ID, UUID.randomUUID());

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.domainEvents()).hasSize(1);
        assertThat(order.domainEvents().getFirst()).isInstanceOf(OrderConfirmed.class);
    }

    @Test
    void shouldNotConfirmOrderTwiceWhenDuplicateReservationEventArrives() {
        Order order = Order.place(CustomerId.of(UUID.randomUUID()), List.of(line("10.00", 1)), CORRELATION_ID);
        order.clearDomainEvents();

        UUID paymentEventId = UUID.randomUUID();
        UUID stockEventId = UUID.randomUUID();
        order.markPaymentReserved(CORRELATION_ID, paymentEventId);
        order.markStockReserved(CORRELATION_ID, stockEventId);
        order.clearDomainEvents();

        order.markStockReserved(CORRELATION_ID, stockEventId);
        order.markPaymentReserved(CORRELATION_ID, paymentEventId);

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.domainEvents()).isEmpty();
    }

    @Test
    void shouldCancelPlacedOrderAndRegisterEvent() {
        Order order = Order.place(CustomerId.of(UUID.randomUUID()), List.of(line("10.00", 1)), CORRELATION_ID);
        order.clearDomainEvents();

        order.cancel(CORRELATION_ID, null);

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.domainEvents()).hasSize(1);
        assertThat(order.domainEvents().getFirst()).isInstanceOf(OrderCancelled.class);
    }

    @Test
    void shouldIgnorePaymentAndStockReservationsForCancelledOrder() {
        Order order = Order.place(CustomerId.of(UUID.randomUUID()), List.of(line("10.00", 1)), CORRELATION_ID);
        order.cancel(CORRELATION_ID, null);
        order.clearDomainEvents();

        order.markPaymentReserved(CORRELATION_ID, UUID.randomUUID());
        order.markStockReserved(CORRELATION_ID, UUID.randomUUID());

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.domainEvents()).isEmpty();
    }

    private static OrderLine line(String unitAmount, int quantity) {
        return new OrderLine(ProductId.newId(), quantity, Money.of(unitAmount, "PLN"));
    }
}
