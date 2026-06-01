package pl.jakubtworek.marketplace.stage2;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.inventory.application.ReserveStockOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.inventory.domain.StockItem;
import pl.jakubtworek.marketplace.inventory.domain.StockReservationFailed;
import pl.jakubtworek.marketplace.inventory.domain.StockReserved;
import pl.jakubtworek.marketplace.inventory.infrastructure.InMemoryStockRepository;
import pl.jakubtworek.marketplace.ordering.application.*;
import pl.jakubtworek.marketplace.ordering.domain.OrderConfirmed;
import pl.jakubtworek.marketplace.ordering.domain.OrderStatus;
import pl.jakubtworek.marketplace.ordering.infrastructure.InMemoryOrderRepository;
import pl.jakubtworek.marketplace.payment.application.PaymentGateway;
import pl.jakubtworek.marketplace.payment.application.ReservePaymentOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.payment.domain.PaymentRejected;
import pl.jakubtworek.marketplace.payment.domain.PaymentReserved;
import pl.jakubtworek.marketplace.payment.domain.PaymentStatus;
import pl.jakubtworek.marketplace.payment.infrastructure.InMemoryPaymentRepository;
import pl.jakubtworek.marketplace.testsupport.TestApplicationEventBus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventDrivenOrderFlowTest {

    @Test
    void shouldConfirmOrderThroughInternalEventsWhenPaymentAndStockAreReserved() {
        var fixture = Fixture.withPaymentAccepted();
        UUID productId = UUID.randomUUID();
        fixture.stockRepository.save(StockItem.create(productId, 10));

        var orderId = fixture.placeOrder(productId, 2);

        var order = fixture.orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.paymentReserved()).isTrue();
        assertThat(order.stockReserved()).isTrue();

        assertThat(fixture.paymentRepository.findByOrderId(orderId.value()).orElseThrow().status())
                .isEqualTo(PaymentStatus.RESERVED);
        assertThat(fixture.stockRepository.findByProductId(productId).orElseThrow().availableQuantity()).isEqualTo(8);
        assertThat(fixture.stockRepository.findByProductId(productId).orElseThrow().reservedQuantity()).isEqualTo(2);

        assertThat(fixture.eventBus.eventsOfType(PaymentReserved.class)).hasSize(1);
        assertThat(fixture.eventBus.eventsOfType(StockReserved.class)).hasSize(1);
        assertThat(fixture.eventBus.eventsOfType(OrderConfirmed.class)).hasSize(1);
    }

    @Test
    void shouldRejectOrderThroughInternalEventWhenPaymentIsRejected() {
        var fixture = Fixture.withPaymentRejected();
        UUID productId = UUID.randomUUID();
        fixture.stockRepository.save(StockItem.create(productId, 10));

        var orderId = fixture.placeOrder(productId, 1);

        var order = fixture.orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(fixture.paymentRepository.findByOrderId(orderId.value()).orElseThrow().status())
                .isEqualTo(PaymentStatus.REJECTED);
        assertThat(fixture.eventBus.eventsOfType(PaymentRejected.class)).hasSize(1);
        assertThat(fixture.eventBus.eventsOfType(OrderConfirmed.class)).isEmpty();
    }

    @Test
    void shouldRejectOrderThroughInternalEventWhenThereIsNotEnoughStock() {
        var fixture = Fixture.withPaymentAccepted();
        UUID productId = UUID.randomUUID();
        fixture.stockRepository.save(StockItem.create(productId, 1));

        var orderId = fixture.placeOrder(productId, 2);

        var order = fixture.orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.paymentReserved()).isTrue();
        assertThat(order.stockReserved()).isFalse();
        assertThat(fixture.stockRepository.findByProductId(productId).orElseThrow().availableQuantity()).isEqualTo(1);
        assertThat(fixture.stockRepository.findByProductId(productId).orElseThrow().reservedQuantity()).isZero();

        assertThat(fixture.eventBus.eventsOfType(StockReservationFailed.class)).hasSize(1);
        assertThat(fixture.eventBus.eventsOfType(OrderConfirmed.class)).isEmpty();
    }

    private static class Fixture {
        private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        private final InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        private final InMemoryStockRepository stockRepository = new InMemoryStockRepository();
        private final TestApplicationEventBus eventBus = new TestApplicationEventBus();
        private final PlaceOrderUseCase placeOrderUseCase;

        private Fixture(boolean paymentAccepted) {
            PaymentGateway paymentGateway = (orderId, amount) -> new PaymentGateway.PaymentReservationResult(
                    paymentAccepted,
                    paymentAccepted ? "accepted in test" : "rejected in test"
            );

            eventBus.register(new ReservePaymentOnOrderPlacedHandler(paymentGateway, paymentRepository, eventBus));
            eventBus.register(new ReserveStockOnOrderPlacedHandler(stockRepository, eventBus));
            eventBus.register(new ConfirmPaymentOnPaymentReservedHandler(orderRepository, eventBus));
            eventBus.register(new ConfirmStockOnStockReservedHandler(orderRepository, eventBus));
            eventBus.register(new RejectOrderOnPaymentRejectedHandler(orderRepository));
            eventBus.register(new RejectOrderOnStockReservationFailedHandler(orderRepository));

            this.placeOrderUseCase = new PlaceOrderUseCase(orderRepository, eventBus);
        }

        static Fixture withPaymentAccepted() {
            return new Fixture(true);
        }

        static Fixture withPaymentRejected() {
            return new Fixture(false);
        }

        pl.jakubtworek.marketplace.ordering.domain.OrderId placeOrder(UUID productId, int quantity) {
            return placeOrderUseCase.handle(new PlaceOrderUseCase.Command(
                    UUID.randomUUID(),
                    List.of(new PlaceOrderUseCase.Line(productId, quantity, "10.00", "PLN")),
                    UUID.randomUUID()
            ));
        }
    }
}
