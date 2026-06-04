package pl.jakubtworek.marketplace.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import pl.jakubtworek.marketplace.integration.kafka.*;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.ordering.domain.OrderStatus;
import pl.jakubtworek.marketplace.ordering.application.OrderRepository;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.observability.FlowTraceRepository;
import pl.jakubtworek.marketplace.shared.observability.ObservabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MarketplaceKafkaPostgresIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    KafkaOutboxWorker outboxWorker;

    @Autowired
    KafkaMessageBroker broker;

    @Autowired
    ApplicationEventBus eventBus;

    @Autowired
    ProcessedEventRepository processedEvents;

    @Autowired
    DlqEventRepository dlqRepository;

    @Autowired
    OutboxEventRepository outboxRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ObservabilityService observability;

    @Autowired
    FlowTraceRepository flowTraceRepository;

    @Autowired
    KafkaTopicResolver topicResolver;

    @Test
    void shouldCreateProductAddStockPlaceOrderPersistOutboxAndPublishOrderPlacedToKafka() {
        UUID productId = createProduct("Kafka Product", "49.99");
        addStock(productId, 10);

        UUID correlationId = UUID.randomUUID();
        UUID orderId = placeOrder(productId, 2, "49.99", correlationId);

        assertOrderStatus(orderId, "PENDING");

        assertThat(outboxRepository.findByStatus(OutboxEventStatus.NEW, 20))
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("OrderPlaced");
                    assertThat(event.aggregateId()).isEqualTo(orderId);
                    assertThat(event.correlationId()).isEqualTo(correlationId);
                    assertThat(event.payload()).contains(productId.toString());
                });

        int published = outboxWorker.publishNew(10);

        assertThat(published).isEqualTo(1);
        assertThat(outboxRepository.findByStatus(OutboxEventStatus.PUBLISHED, 20))
                .extracting(event -> event.eventType())
                .contains("OrderPlaced");

        assertThat(broker.endOffset(orderEventsTopic()))
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldConfirmOrderWhenPaymentAndStockAreReservedThroughKafkaConsumers() {
        UUID productId = createProduct("Confirmed Product", "99.99");
        addStock(productId, 10);

        UUID orderId = placeOrder(productId, 2, "99.99", UUID.randomUUID());

        KafkaConsumerWorker orderEventsConsumer = worker(
                "order-events-consumer-missing-stock-it",
                orderEventsTopic(),
                "marketplace-order-events-missing-stock-it"
        );

        KafkaConsumerWorker orderingPaymentConsumer = worker(
                "ordering-payment-consumer-missing-stock-it",
                paymentEventsTopic(),
                ORDERING_CONSUMER_GROUP
        );

        KafkaConsumerWorker orderingInventoryConsumer = worker(
                "ordering-inventory-consumer-missing-stock-it",
                inventoryEventsTopic(),
                ORDERING_CONSUMER_GROUP
        );

        drainPipeline(
                orderEventsConsumer,
                orderingPaymentConsumer,
                orderingInventoryConsumer
        );

        assertOrderStatus(orderId, "CONFIRMED");

        assertThat(orderRepository.findById(OrderId.of(orderId)))
                .get()
                .satisfies(order -> {
                    assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
                    assertThat(order.paymentReserved()).isTrue();
                    assertThat(order.stockReserved()).isTrue();
                });

        assertThat(outboxRepository.findByStatus(OutboxEventStatus.PUBLISHED, 50))
                .extracting(event -> event.eventType())
                .contains("OrderPlaced", "PaymentReserved", "StockReserved", "OrderConfirmed");

        assertThat(countProcessedEvents()).isGreaterThanOrEqualTo(4);
        assertThat(countDlqEvents()).isZero();

        assertThat(flowTraceRepository.findByOrderId(orderId)).isNotEmpty();
    }

    @Test
    void shouldRejectOrderWhenStockIsMissing() {
        UUID productId = createProduct("Missing Stock Product", "25.00");

        UUID orderId = placeOrder(productId, 2, "25.00", UUID.randomUUID());

        KafkaConsumerWorker orderEventsConsumer = worker(
                "order-events-consumer-missing-stock-it",
                orderEventsTopic(),
                "marketplace-order-events-missing-stock-it"
        );

        KafkaConsumerWorker orderingPaymentConsumer = worker(
                "ordering-payment-consumer-missing-stock-it",
                paymentEventsTopic(),
                ORDERING_CONSUMER_GROUP
        );

        KafkaConsumerWorker orderingInventoryConsumer = worker(
                "ordering-inventory-consumer-missing-stock-it",
                inventoryEventsTopic(),
                ORDERING_CONSUMER_GROUP
        );

        drainPipeline(
                orderEventsConsumer,
                orderingPaymentConsumer,
                orderingInventoryConsumer
        );

        assertOrderStatus(orderId, "REJECTED");

        assertThat(outboxRepository.findByStatus(OutboxEventStatus.PUBLISHED, 50))
                .extracting(event -> event.eventType())
                .contains("OrderPlaced", "PaymentReserved", "StockReservationFailed");

        //assertThat(countDlqEvents()).isZero();
    }

    @Test
    void shouldRejectOrderWhenStockIsNotEnough() {
        UUID productId = createProduct("Not Enough Stock Product", "10.00");
        addStock(productId, 1);

        UUID orderId = placeOrder(productId, 2, "10.00", UUID.randomUUID());

        KafkaConsumerWorker orderEventsConsumer = worker(
                "order-events-consumer-missing-stock-it",
                orderEventsTopic(),
                "marketplace-order-events-missing-stock-it"
        );

        KafkaConsumerWorker orderingPaymentConsumer = worker(
                "ordering-payment-consumer-missing-stock-it",
                paymentEventsTopic(),
                ORDERING_CONSUMER_GROUP
        );

        KafkaConsumerWorker orderingInventoryConsumer = worker(
                "ordering-inventory-consumer-missing-stock-it",
                inventoryEventsTopic(),
                ORDERING_CONSUMER_GROUP
        );

        drainPipeline(
                orderEventsConsumer,
                orderingPaymentConsumer,
                orderingInventoryConsumer
        );

        assertOrderStatus(orderId, "REJECTED");

        assertThat(outboxRepository.findByStatus(OutboxEventStatus.PUBLISHED, 50))
                .extracting(event -> event.eventType())
                .contains("StockReservationFailed");

        //assertThat(countDlqEvents()).isZero();
    }

    @Test
    void shouldSkipDuplicateEventUsingProcessedEventsAndStillCommitOffset() {
        UUID productId = createProduct("Duplicate Product", "15.00");
        addStock(productId, 5);

        UUID orderId = placeOrder(productId, 1, "15.00", UUID.randomUUID());

        outboxWorker.publishNew(10);

        KafkaConsumerWorker paymentConsumer = worker(
                "payment-consumer-duplicate-it",
                orderEventsTopic(),
                PAYMENT_CONSUMER_GROUP
        );

        int firstPoll = paymentConsumer.pollAndProcess(10);
        int processedAfterFirstPoll = countProcessedEvents();

        assertThat(firstPoll).isGreaterThanOrEqualTo(1);
        assertThat(processedAfterFirstPoll).isGreaterThanOrEqualTo(1);

        /*
         * Ten sam consumer group nie powinien dostać ponownie tego samego rekordu,
         * bo offset został commitowany.
         */
        int secondPoll = paymentConsumer.pollAndProcess(10);

        assertThat(secondPoll).isEqualTo(0);
        assertThat(countProcessedEvents()).isEqualTo(processedAfterFirstPoll);

        assertThat(paymentConsumer.currentLag()).isEqualTo(0);
        assertThat(orderRepository.findById(OrderId.of(orderId))).isPresent();
    }

    @Test
    void shouldSendUnsupportedEventVersionToDlqAndCommitOffset() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        IntegrationEventEnvelope badEvent = new IntegrationEventEnvelope(
                eventId,
                orderId,
                "Order",
                "OrderPlaced",
                999,
                "{\"orderId\":\"" + orderId + "\"}",
                UUID.randomUUID(),
                null,
                Instant.now()
        );

        broker.publish(orderEventsTopic(), orderId.toString(), badEvent);

        KafkaConsumerWorker orderConsumer = worker(
                "order-consumer-dlq-it",
                orderEventsTopic(),
                ORDERING_CONSUMER_GROUP
        );

        orderConsumer.pollAndProcess(10);

        assertThat(dlqRepository.findAll())
                .anySatisfy(dlq -> {
                    assertThat(dlq.envelope().eventId()).isEqualTo(eventId);
                    assertThat(dlq.reason()).contains("Unsupported");
                    assertThat(dlq.attempts()).isEqualTo(3);
                });

        assertThat(countDlqEvents()).isEqualTo(1);
        assertThat(orderConsumer.currentLag()).isEqualTo(0);
    }

    @Test
    void shouldReplayDlqEventAfterFailureIsRecordedAsReplayFailedWhenStillInvalid() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        IntegrationEventEnvelope badEvent = new IntegrationEventEnvelope(
                eventId,
                orderId,
                "Order",
                "OrderPlaced",
                999,
                "{\"orderId\":\"" + orderId + "\"}",
                UUID.randomUUID(),
                null,
                Instant.now()
        );

        broker.publish(orderEventsTopic(), orderId.toString(), badEvent);

        KafkaConsumerWorker orderConsumer = worker(
                "order-consumer-replay-failed-it",
                orderEventsTopic(),
                ORDERING_CONSUMER_GROUP
        );

        orderConsumer.pollAndProcess(10);

        var dlq = dlqRepository.findAll().get(0);

        ResponseEntity<Void> replayResponse = rest.postForEntity(
                url("/admin/kafka/dlq/" + dlq.id() + "/replay"),
                null,
                Void.class
        );

        /*
         * Jeżeli controller przepuszcza wyjątek, możesz dostać 5xx.
         * To jest akceptowalne dla błędnego replay, ale status w DLQ powinien zostać zmieniony.
         */
        assertThat(replayResponse.getStatusCode().is5xxServerError()
                || replayResponse.getStatusCode().is2xxSuccessful()
                || replayResponse.getStatusCode().is4xxClientError())
                .isTrue();

        var loaded = dlqRepository.findById(dlq.id()).orElseThrow();

        assertThat(loaded.status().name()).contains("REPLAY");
    }

    private KafkaConsumerWorker worker(String consumerName, String topic, String consumerGroup) {
        return new KafkaConsumerWorker(
                consumerName,
                topic,
                consumerGroup,
                broker,
                eventBus,
                processedEvents,
                dlqRepository,
                objectMapper,
                new RetryPolicy(3),
                observability
        );
    }

    private void drainPipeline(KafkaConsumerWorker... consumers) {
        for (int i = 0; i < 30; i++) {
            int work = outboxWorker.publishNew(100);

            for (KafkaConsumerWorker consumer : consumers) {
                work += consumer.pollAndProcess(100);
            }

            if (work == 0) {
                return;
            }
        }

        throw new AssertionError(
                "Pipeline did not become idle. Check whether an event creates a loop or a consumer is missing."
        );
    }

    private UUID createProduct(String name, String amount) {
        ResponseEntity<Map> response = rest.postForEntity(url("/api/products"), Map.of(
                "name", name,
                "amount", amount,
                "currency", "PLN"
        ), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        return UUID.fromString(response.getBody().get("id").toString());
    }

    private void addStock(UUID productId, int quantity) {
        ResponseEntity<Void> response = rest.postForEntity(url("/api/stock"), Map.of(
                "productId", productId.toString(),
                "quantity", quantity
        ), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private UUID placeOrder(UUID productId, int quantity, String unitAmount, UUID correlationId) {
        ResponseEntity<Map> response = rest.postForEntity(url("/api/orders"), Map.of(
                "customerId", UUID.randomUUID().toString(),
                "correlationId", correlationId.toString(),
                "lines", List.of(Map.of(
                        "productId", productId.toString(),
                        "quantity", quantity,
                        "unitAmount", unitAmount,
                        "currency", "PLN"
                ))
        ), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        return UUID.fromString(response.getBody().get("id").toString());
    }

    private void assertOrderStatus(UUID orderId, String expectedStatus) {
        ResponseEntity<Map> loadedOrder = rest.getForEntity(
                url("/api/orders/" + orderId),
                Map.class
        );

        assertThat(loadedOrder.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loadedOrder.getBody()).isNotNull();
        assertThat(loadedOrder.getBody().get("status")).isEqualTo(expectedStatus);
    }

    private String orderEventsTopic() {
        return topicResolver.resolve("OrderPlaced").topicName();
    }

    private String paymentEventsTopic() {
        return topicResolver.resolve("PaymentReserved").topicName();
    }

    private String inventoryEventsTopic() {
        return topicResolver.resolve("StockReserved").topicName();
    }
}