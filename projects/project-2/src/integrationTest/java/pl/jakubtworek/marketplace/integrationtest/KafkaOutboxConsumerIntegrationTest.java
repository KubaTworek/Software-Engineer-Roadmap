package pl.jakubtworek.marketplace.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pl.jakubtworek.marketplace.integration.kafka.*;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.InMemoryDlqEventRepository;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.InMemoryKafkaBroker;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.InMemoryProcessedEventRepository;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.observability.ObservabilityService;
import pl.jakubtworek.marketplace.shared.observability.FlowTraceRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaOutboxConsumerIntegrationTest extends AbstractPostgresIntegrationTest {
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
    ObjectMapper objectMapper;
    @Autowired
    ObservabilityService observability;
    @Autowired
    OutboxEventRepository outboxRepository;
    @Autowired
    FlowTraceRepository flowTraceRepository;

    @BeforeEach
    void clearInMemoryKafkaState() {
        if (broker instanceof InMemoryKafkaBroker inMemoryKafkaBroker) {
            inMemoryKafkaBroker.clear();
        }
        if (dlqRepository instanceof InMemoryDlqEventRepository inMemoryDlqEventRepository) {
            inMemoryDlqEventRepository.clear();
        }
        if (processedEvents instanceof InMemoryProcessedEventRepository inMemoryProcessedEventRepository) {
            inMemoryProcessedEventRepository.clear();
        }
    }

    @Test
    void processesOrderFlowAsynchronouslyThroughOutboxKafkaConsumersAndProcessedEvents() {
        UUID productId = createProduct();
        addStock(productId, 10);

        UUID orderId = placeOrder(productId, 2, UUID.randomUUID());

        KafkaConsumerWorker orderConsumer = worker("order-consumer-it", KafkaTopic.ORDER_EVENTS.topicName());
        KafkaConsumerWorker paymentConsumer = worker("payment-consumer-it", KafkaTopic.PAYMENT_EVENTS.topicName());
        KafkaConsumerWorker inventoryConsumer = worker("inventory-consumer-it", KafkaTopic.INVENTORY_EVENTS.topicName());

        drainPipeline(orderConsumer, paymentConsumer, inventoryConsumer);

        ResponseEntity<Map> loadedOrder = rest.getForEntity(url("/api/orders/" + orderId), Map.class);
        assertThat(loadedOrder.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loadedOrder.getBody().get("status")).isEqualTo("CONFIRMED");

        assertThat(outboxRepository.findByStatus(OutboxEventStatus.NEW, 20)).isEmpty();
        assertThat(outboxRepository.findByStatus(OutboxEventStatus.PUBLISHED, 20))
                .extracting(event -> event.eventType())
                .contains("OrderPlaced", "PaymentReserved", "StockReserved", "OrderConfirmed");

        assertThat(orderConsumer.currentLag()).isEqualTo(0);
        assertThat(paymentConsumer.currentLag()).isEqualTo(0);
        assertThat(inventoryConsumer.currentLag()).isEqualTo(0);
        assertThat(flowTraceRepository.findByOrderId(orderId)).isNotEmpty();
    }

    @Test
    void sendsUnsupportedEventVersionToDlqAndRecordsReason() {
        UUID eventId = UUID.randomUUID();
        IntegrationEventEnvelope badEvent = new IntegrationEventEnvelope(
                eventId,
                UUID.randomUUID(),
                "Order",
                "OrderPlaced",
                999,
                "{\"orderId\":\"" + UUID.randomUUID() + "\"}",
                UUID.randomUUID(),
                null,
                Instant.now()
        );

        broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), badEvent.aggregateId().toString(), badEvent);

        KafkaConsumerWorker orderConsumer = worker("order-consumer-dlq-it", KafkaTopic.ORDER_EVENTS.topicName());
        orderConsumer.pollAndProcess(10);

        assertThat(dlqRepository.findAll())
                .anySatisfy(dlq -> {
                    assertThat(dlq.envelope().eventId()).isEqualTo(eventId);
                    assertThat(dlq.reason()).contains("Unsupported");
                    assertThat(dlq.attempts()).isEqualTo(2);
                });
        assertThat(orderConsumer.currentLag()).isEqualTo(0);
    }

    private KafkaConsumerWorker worker(String consumerName, String topic) {
        return new KafkaConsumerWorker(
                consumerName,
                topic,
                "marketplace-integration-test-group",
                broker,
                eventBus,
                processedEvents,
                dlqRepository,
                objectMapper,
                new RetryPolicy(2),
                observability
        );
    }

    private void drainPipeline(KafkaConsumerWorker... consumers) {
        for (int i = 0; i < 20; i++) {
            int work = outboxWorker.publishNew(100);
            for (KafkaConsumerWorker consumer : consumers) {
                work += consumer.pollAndProcess(100);
            }
            if (work == 0) {
                return;
            }
        }
        throw new AssertionError("Pipeline did not become idle. Check whether an event creates a loop or a consumer is missing.");
    }

    private UUID createProduct() {
        ResponseEntity<Map> response = rest.postForEntity(url("/api/products"), Map.of(
                "name", "Async Flow Product",
                "amount", "99.99",
                "currency", "PLN"
        ), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private void addStock(UUID productId, int quantity) {
        ResponseEntity<Void> response = rest.postForEntity(url("/api/stock"), Map.of(
                "productId", productId.toString(),
                "quantity", quantity
        ), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private UUID placeOrder(UUID productId, int quantity, UUID correlationId) {
        ResponseEntity<Map> response = rest.postForEntity(url("/api/orders"), Map.of(
                "customerId", UUID.randomUUID().toString(),
                "correlationId", correlationId.toString(),
                "lines", List.of(Map.of(
                        "productId", productId.toString(),
                        "quantity", quantity,
                        "unitAmount", "99.99",
                        "currency", "PLN"
                ))
        ), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }
}
