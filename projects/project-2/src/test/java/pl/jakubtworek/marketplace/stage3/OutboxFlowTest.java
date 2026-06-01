package pl.jakubtworek.marketplace.stage3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.integration.outbox.*;
import pl.jakubtworek.marketplace.integration.outbox.infrastructure.InMemoryOutboxEventRepository;
import pl.jakubtworek.marketplace.inventory.application.ReserveStockOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.inventory.domain.StockItem;
import pl.jakubtworek.marketplace.ordering.application.*;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.ordering.domain.OrderStatus;
import pl.jakubtworek.marketplace.ordering.infrastructure.InMemoryOrderRepository;
import pl.jakubtworek.marketplace.payment.application.PaymentGateway;
import pl.jakubtworek.marketplace.payment.application.ReservePaymentOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.payment.domain.PaymentStatus;
import pl.jakubtworek.marketplace.payment.infrastructure.InMemoryPaymentRepository;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxFlowTest {

    @Test
    void shouldStoreOrderPlacedInOutboxAndPublishItLaterThroughWorker() {
        var fixture = Fixture.withPaymentAccepted();
        UUID productId = UUID.randomUUID();
        fixture.stockRepository.save(StockItem.create(productId, 10));

        var orderId = fixture.placeOrder(productId, 2);

        var orderBeforeWorker = fixture.orderRepository.findById(orderId).orElseThrow();
        assertThat(orderBeforeWorker.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(eventTypes(fixture.outboxRepository.findByStatus(OutboxEventStatus.NEW, 100)))
                .containsExactly("OrderPlaced");

        int processed = fixture.outboxWorker.publishUntilIdle(10, 10);

        assertThat(processed).isEqualTo(4); // OrderPlaced, PaymentReserved, StockReserved, OrderConfirmed
        assertThat(fixture.outboxRepository.findByStatus(OutboxEventStatus.NEW, 100)).isEmpty();
        assertThat(fixture.outboxRepository.findByStatus(OutboxEventStatus.FAILED, 100)).isEmpty();
        assertThat(eventTypes(fixture.outboxRepository.findByStatus(OutboxEventStatus.PUBLISHED, 100)))
                .contains("OrderPlaced", "PaymentReserved", "StockReserved", "OrderConfirmed");

        var order = fixture.orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.paymentReserved()).isTrue();
        assertThat(order.stockReserved()).isTrue();
        assertThat(fixture.paymentRepository.findByOrderId(orderId.value()).orElseThrow().status())
                .isEqualTo(PaymentStatus.RESERVED);
        assertThat(fixture.stockRepository.findByProductId(productId).orElseThrow().availableQuantity()).isEqualTo(8);
    }

    @Test
    void shouldKeepFailedOutboxEventForRetryWhenHandlerFails() {
        var outboxRepository = new InMemoryOutboxEventRepository();
        var mapper = new OutboxEventMapper(new ObjectMapper());
        var outboxPublisher = new OutboxEventPublisher(outboxRepository, mapper);
        var orderRepository = new InMemoryOrderRepository();
        AtomicInteger attempts = new AtomicInteger();

        DomainEventHandler<OrderPlaced> flakyHandler = new DomainEventHandler<>() {
            @Override
            public Class<OrderPlaced> eventType() {
                return OrderPlaced.class;
            }

            @Override
            public void handle(OrderPlaced event) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("temporary payment dependency failure");
                }
            }
        };

        var eventBus = new ApplicationEventBus(List.of(flakyHandler));
        var worker = new OutboxWorker(outboxRepository, eventBus, mapper);
        var placeOrderUseCase = new PlaceOrderUseCase(orderRepository, outboxPublisher);

        placeOrderUseCase.handle(new PlaceOrderUseCase.Command(
                UUID.randomUUID(),
                List.of(new PlaceOrderUseCase.Line(UUID.randomUUID(), 1, "10.00", "PLN")),
                UUID.randomUUID()
        ));

        worker.publishNew(10);

        var failed = outboxRepository.findByStatus(OutboxEventStatus.FAILED, 10);
        assertThat(failed).hasSize(1);
        assertThat(failed.getFirst().retryCount()).isEqualTo(1);
        assertThat(failed.getFirst().lastError()).contains("temporary payment dependency failure");

        worker.retryManually(failed.getFirst().id());

        assertThat(attempts).hasValue(2);
        assertThat(outboxRepository.findByStatus(OutboxEventStatus.FAILED, 10)).isEmpty();
        assertThat(outboxRepository.findByStatus(OutboxEventStatus.PUBLISHED, 10)).hasSize(1);
    }

    @Test
    void shouldRejectOrderWhenOutboxPublishesStockFailureEvent() {
        var fixture = Fixture.withPaymentAccepted();
        UUID productId = UUID.randomUUID();
        fixture.stockRepository.save(StockItem.create(productId, 1));

        var orderId = fixture.placeOrder(productId, 2);
        fixture.outboxWorker.publishUntilIdle(10, 10);

        var order = fixture.orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.paymentReserved()).isTrue();
        assertThat(order.stockReserved()).isFalse();
        assertThat(eventTypes(fixture.outboxRepository.findByStatus(OutboxEventStatus.PUBLISHED, 100)))
                .contains("OrderPlaced", "PaymentReserved", "StockReservationFailed");
    }

    private static List<String> eventTypes(List<OutboxEvent> events) {
        return events.stream().map(OutboxEvent::eventType).toList();
    }

    private static class Fixture {
        private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        private final InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        private final pl.jakubtworek.marketplace.inventory.infrastructure.InMemoryStockRepository stockRepository = new pl.jakubtworek.marketplace.inventory.infrastructure.InMemoryStockRepository();
        private final InMemoryOutboxEventRepository outboxRepository = new InMemoryOutboxEventRepository();
        private final OutboxWorker outboxWorker;
        private final PlaceOrderUseCase placeOrderUseCase;

        private Fixture(boolean paymentAccepted) {
            var mapper = new OutboxEventMapper(new ObjectMapper());
            var outboxPublisher = new OutboxEventPublisher(outboxRepository, mapper);

            PaymentGateway paymentGateway = (orderId, amount) -> new PaymentGateway.PaymentReservationResult(
                    paymentAccepted,
                    paymentAccepted ? "accepted in test" : "rejected in test"
            );

            List<DomainEventHandler<?>> handlers = List.of(
                    new ReservePaymentOnOrderPlacedHandler(paymentGateway, paymentRepository, outboxPublisher),
                    new ReserveStockOnOrderPlacedHandler(stockRepository, outboxPublisher),
                    new ConfirmPaymentOnPaymentReservedHandler(orderRepository, outboxPublisher),
                    new ConfirmStockOnStockReservedHandler(orderRepository, outboxPublisher),
                    new RejectOrderOnPaymentRejectedHandler(orderRepository),
                    new RejectOrderOnStockReservationFailedHandler(orderRepository)
            );

            var eventBus = new ApplicationEventBus(handlers);
            this.outboxWorker = new OutboxWorker(outboxRepository, eventBus, mapper);
            this.placeOrderUseCase = new PlaceOrderUseCase(orderRepository, outboxPublisher);
        }

        static Fixture withPaymentAccepted() {
            return new Fixture(true);
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
