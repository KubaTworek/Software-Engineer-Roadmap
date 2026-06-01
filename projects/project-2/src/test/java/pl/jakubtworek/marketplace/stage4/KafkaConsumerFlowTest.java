package pl.jakubtworek.marketplace.stage4;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.integration.kafka.*;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.*;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventPublisher;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventMapper;
import pl.jakubtworek.marketplace.integration.outbox.infrastructure.InMemoryOutboxEventRepository;
import pl.jakubtworek.marketplace.inventory.application.ReserveStockOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.inventory.domain.StockItem;
import pl.jakubtworek.marketplace.ordering.application.*;
import pl.jakubtworek.marketplace.ordering.domain.OrderStatus;
import pl.jakubtworek.marketplace.ordering.infrastructure.InMemoryOrderRepository;
import pl.jakubtworek.marketplace.payment.application.PaymentGateway;
import pl.jakubtworek.marketplace.payment.application.ReservePaymentOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.payment.domain.PaymentStatus;
import pl.jakubtworek.marketplace.payment.infrastructure.InMemoryPaymentRepository;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class KafkaConsumerFlowTest {

    @Test
    void shouldProcessFullOrderFlowAsynchronouslyThroughKafkaTopics() {
        var fixture = Fixture.withPaymentAccepted();
        UUID productId = UUID.randomUUID();
        fixture.stockRepository.save(StockItem.create(productId, 10));

        var orderId = fixture.placeOrder(productId, 2);
        assertThat(fixture.orderRepository.findById(orderId).orElseThrow().status()).isEqualTo(OrderStatus.PENDING);

        fixture.kafkaOutboxWorker.publishNew(10);
        assertThat(fixture.broker.records(KafkaTopic.ORDER_EVENTS.topicName())).hasSize(1);

        fixture.paymentConsumer.pollAndProcess(10);
        fixture.inventoryConsumer.pollAndProcess(10);
        fixture.kafkaOutboxWorker.publishNew(10);

        assertThat(fixture.broker.records(KafkaTopic.PAYMENT_EVENTS.topicName())).hasSize(1);
        assertThat(fixture.broker.records(KafkaTopic.INVENTORY_EVENTS.topicName())).hasSize(1);

        fixture.orderingPaymentConsumer.pollAndProcess(10);
        fixture.orderingInventoryConsumer.pollAndProcess(10);
        fixture.kafkaOutboxWorker.publishNew(10);

        var order = fixture.orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.paymentReserved()).isTrue();
        assertThat(order.stockReserved()).isTrue();
        assertThat(fixture.paymentRepository.findByOrderId(orderId.value()).orElseThrow().status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(fixture.stockRepository.findByProductId(productId).orElseThrow().availableQuantity()).isEqualTo(8);
        assertThat(fixture.broker.records(KafkaTopic.ORDER_EVENTS.topicName()))
                .extracting(record -> record.envelope().eventType())
                .contains("OrderPlaced", "OrderConfirmed");
    }

    @Test
    void duplicateEventShouldNotCorruptDataBecauseConsumerUsesProcessedEvents() {
        var fixture = Fixture.withPaymentAccepted();
        UUID productId = UUID.randomUUID();
        fixture.stockRepository.save(StockItem.create(productId, 10));

        fixture.placeOrder(productId, 2);
        fixture.kafkaOutboxWorker.publishNew(10);
        var original = fixture.broker.records(KafkaTopic.ORDER_EVENTS.topicName()).getFirst();

        // Real Kafka may redeliver the same event. Here we simulate the same eventId being present twice.
        fixture.broker.publish(original.topic(), original.key(), original.envelope());
        assertThat(fixture.broker.records(KafkaTopic.ORDER_EVENTS.topicName())).hasSize(2);

        fixture.paymentConsumer.pollAndProcess(10);
        fixture.kafkaOutboxWorker.publishNew(10);

        assertThat(fixture.broker.records(KafkaTopic.PAYMENT_EVENTS.topicName()))
                .extracting(record -> record.envelope().eventType())
                .containsExactly("PaymentReserved");
        assertThat(fixture.processedEvents.exists(original.envelope().eventId(), "payment-consumer")).isTrue();
    }

    @Test
    void crashAfterSideEffectBeforeOffsetCommitShouldBeSafeOnRedelivery() {
        var fixture = Fixture.withPaymentAccepted();
        UUID productId = UUID.randomUUID();
        fixture.stockRepository.save(StockItem.create(productId, 10));

        var orderId = fixture.placeOrder(productId, 2);
        fixture.kafkaOutboxWorker.publishNew(10);
        var record = fixture.broker.records(KafkaTopic.ORDER_EVENTS.topicName()).getFirst();

        assertThatThrownBy(() -> fixture.paymentConsumer.simulateCrashAfterSuccessfulProcessingBeforeCommit(record))
                .isInstanceOf(SimulatedConsumerCrashException.class);

        assertThat(fixture.broker.committedOffset(KafkaTopic.ORDER_EVENTS.topicName(), "payment-group")).isEqualTo(-1);
        assertThat(fixture.paymentRepository.findByOrderId(orderId.value())).isPresent();

        // Same record is visible again because offset was not committed. It is skipped using processed_events and then committed.
        fixture.paymentConsumer.pollAndProcess(10);

        fixture.kafkaOutboxWorker.publishNew(10);
        assertThat(fixture.broker.records(KafkaTopic.PAYMENT_EVENTS.topicName()))
                .extracting(event -> event.envelope().eventType())
                .containsExactly("PaymentReserved");
        assertThat(fixture.broker.committedOffset(KafkaTopic.ORDER_EVENTS.topicName(), "payment-group")).isEqualTo(0);
    }

    @Test
    void invalidEventShouldGoToDlqAfterRetriesAndCanBeReplayedAfterFixingPayload() {
        var fixture = Fixture.withPaymentAccepted();
        AtomicInteger attempts = new AtomicInteger();

        DomainEventHandler<pl.jakubtworek.marketplace.ordering.domain.OrderPlaced> flakyHandler = new DomainEventHandler<>() {
            @Override
            public Class<pl.jakubtworek.marketplace.ordering.domain.OrderPlaced> eventType() {
                return pl.jakubtworek.marketplace.ordering.domain.OrderPlaced.class;
            }

            @Override
            public void handle(pl.jakubtworek.marketplace.ordering.domain.OrderPlaced event) {
                if (attempts.incrementAndGet() <= 3) {
                    throw new IllegalStateException("temporary handler failure");
                }
            }
        };

        var eventBus = new ApplicationEventBus(List.of(flakyHandler));
        var consumer = new KafkaConsumerWorker(
                "flaky-consumer",
                KafkaTopic.ORDER_EVENTS.topicName(),
                "flaky-group",
                fixture.broker,
                eventBus,
                new InMemoryProcessedEventRepository(),
                fixture.dlqRepository,
                fixture.objectMapper,
                new RetryPolicy(3)
        );

        var envelope = validOrderPlacedEnvelope();
        fixture.broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), envelope.aggregateId().toString(), envelope);

        consumer.pollAndProcess(10);

        var dlqEvents = fixture.dlqRepository.findByStatus(DlqEventStatus.NEW, 10);
        assertThat(dlqEvents).hasSize(1);
        assertThat(dlqEvents.getFirst().reason()).contains("temporary handler failure");
        assertThat(dlqEvents.getFirst().attempts()).isEqualTo(3);
        assertThat(fixture.broker.committedOffset(KafkaTopic.ORDER_EVENTS.topicName(), "flaky-group")).isEqualTo(0);

        var replayService = new DlqReplayService(fixture.dlqRepository, eventBus, fixture.objectMapper);
        replayService.replay(dlqEvents.getFirst().id());

        assertThat(attempts).hasValue(4);
        assertThat(fixture.dlqRepository.findById(dlqEvents.getFirst().id()).orElseThrow().status())
                .isEqualTo(DlqEventStatus.REPLAYED);
    }

    @Test
    void unsupportedEventTypeShouldGoToDlq() {
        var fixture = Fixture.withPaymentAccepted();
        var badEnvelope = new IntegrationEventEnvelope(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Unknown",
                "UnsupportedEvent",
                1,
                "{\"field\":\"value\"}",
                UUID.randomUUID(),
                null,
                Instant.now()
        );
        fixture.broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), badEnvelope.aggregateId().toString(), badEnvelope);

        fixture.paymentConsumer.pollAndProcess(10);

        var dlqEvents = fixture.dlqRepository.findByStatus(DlqEventStatus.NEW, 10);
        assertThat(dlqEvents).hasSize(1);
        assertThat(dlqEvents.getFirst().reason()).contains("Unsupported outbox event type");
    }

    private static IntegrationEventEnvelope validOrderPlacedEnvelope() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String payload = """
                {
                  "eventId": "%s",
                  "aggregateId": "%s",
                  "customerId": "%s",
                  "total": { "amount": 10.00, "currency": "PLN" },
                  "lines": [
                    {
                      "productId": "%s",
                      "quantity": 1,
                      "unitPrice": { "amount": 10.00, "currency": "PLN" }
                    }
                  ],
                  "occurredAt": "%s",
                  "correlationId": "%s",
                  "causationId": null
                }
                """.formatted(UUID.randomUUID(), orderId, UUID.randomUUID(), productId, Instant.now(), UUID.randomUUID());

        return new IntegrationEventEnvelope(
                UUID.randomUUID(),
                orderId,
                "Order",
                "OrderPlaced",
                2,
                payload,
                UUID.randomUUID(),
                null,
                Instant.now()
        );
    }

    private static class Fixture {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        private final InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        private final pl.jakubtworek.marketplace.inventory.infrastructure.InMemoryStockRepository stockRepository = new pl.jakubtworek.marketplace.inventory.infrastructure.InMemoryStockRepository();
        private final InMemoryOutboxEventRepository outboxRepository = new InMemoryOutboxEventRepository();
        private final InMemoryKafkaBroker broker = new InMemoryKafkaBroker();
        private final InMemoryProcessedEventRepository processedEvents = new InMemoryProcessedEventRepository();
        private final InMemoryDlqEventRepository dlqRepository = new InMemoryDlqEventRepository();
        private final KafkaOutboxWorker kafkaOutboxWorker;
        private final PlaceOrderUseCase placeOrderUseCase;
        private final KafkaConsumerWorker paymentConsumer;
        private final KafkaConsumerWorker inventoryConsumer;
        private final KafkaConsumerWorker orderingPaymentConsumer;
        private final KafkaConsumerWorker orderingInventoryConsumer;

        private Fixture(boolean paymentAccepted) {
            var mapper = new OutboxEventMapper(objectMapper);
            var outboxPublisher = new OutboxEventPublisher(outboxRepository, mapper);
            this.kafkaOutboxWorker = new KafkaOutboxWorker(outboxRepository, broker);

            PaymentGateway paymentGateway = (orderId, amount) -> new PaymentGateway.PaymentReservationResult(
                    paymentAccepted,
                    paymentAccepted ? "accepted in test" : "rejected in test"
            );

            var paymentEventBus = new ApplicationEventBus(List.of(
                    new ReservePaymentOnOrderPlacedHandler(paymentGateway, paymentRepository, outboxPublisher)
            ));
            var inventoryEventBus = new ApplicationEventBus(List.of(
                    new ReserveStockOnOrderPlacedHandler(stockRepository, outboxPublisher)
            ));
            List<DomainEventHandler<?>> orderingHandlers = List.of(
                    new ConfirmPaymentOnPaymentReservedHandler(orderRepository, outboxPublisher),
                    new ConfirmStockOnStockReservedHandler(orderRepository, outboxPublisher),
                    new RejectOrderOnPaymentRejectedHandler(orderRepository),
                    new RejectOrderOnStockReservationFailedHandler(orderRepository)
            );
            var orderingEventBus = new ApplicationEventBus(orderingHandlers);

            this.paymentConsumer = new KafkaConsumerWorker(
                    "payment-consumer",
                    KafkaTopic.ORDER_EVENTS.topicName(),
                    "payment-group",
                    broker,
                    paymentEventBus,
                    processedEvents,
                    dlqRepository,
                    objectMapper,
                    new RetryPolicy(3)
            );
            this.inventoryConsumer = new KafkaConsumerWorker(
                    "inventory-consumer",
                    KafkaTopic.ORDER_EVENTS.topicName(),
                    "inventory-group",
                    broker,
                    inventoryEventBus,
                    processedEvents,
                    dlqRepository,
                    objectMapper,
                    new RetryPolicy(3)
            );
            this.orderingPaymentConsumer = new KafkaConsumerWorker(
                    "ordering-payment-consumer",
                    KafkaTopic.PAYMENT_EVENTS.topicName(),
                    "ordering-group-payment",
                    broker,
                    orderingEventBus,
                    processedEvents,
                    dlqRepository,
                    objectMapper,
                    new RetryPolicy(3)
            );
            this.orderingInventoryConsumer = new KafkaConsumerWorker(
                    "ordering-inventory-consumer",
                    KafkaTopic.INVENTORY_EVENTS.topicName(),
                    "ordering-group-inventory",
                    broker,
                    orderingEventBus,
                    processedEvents,
                    dlqRepository,
                    objectMapper,
                    new RetryPolicy(3)
            );
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
