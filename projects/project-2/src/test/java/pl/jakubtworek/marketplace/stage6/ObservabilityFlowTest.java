package pl.jakubtworek.marketplace.stage6;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.integration.kafka.*;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.InMemoryDlqEventRepository;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.InMemoryKafkaBroker;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.InMemoryProcessedEventRepository;
import pl.jakubtworek.marketplace.payment.application.PaymentGateway;
import pl.jakubtworek.marketplace.payment.application.ReservePaymentOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.payment.infrastructure.InMemoryPaymentRepository;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.observability.*;
import pl.jakubtworek.marketplace.testsupport.RecordingEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityFlowTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldTraceSingleOrderFlowByCorrelationId() {
        var broker = new InMemoryKafkaBroker();
        var processedEvents = new InMemoryProcessedEventRepository();
        var dlqRepository = new InMemoryDlqEventRepository();
        var traceRepository = new InMemoryFlowTraceRepository();
        var metrics = new MarketplaceMetrics();
        var observability = new ObservabilityService(traceRepository, metrics);
        var paymentRepository = new InMemoryPaymentRepository();
        var publisher = new RecordingEventPublisher();

        PaymentGateway gateway = (orderId, amount) -> new PaymentGateway.PaymentReservationResult(true, "accepted");
        var eventBus = new ApplicationEventBus(List.of(
                new ReservePaymentOnOrderPlacedHandler(gateway, paymentRepository, publisher)
        ));
        var consumer = new KafkaConsumerWorker(
                "payment-consumer",
                KafkaTopic.ORDER_EVENTS.topicName(),
                "payment-group",
                broker,
                eventBus,
                processedEvents,
                dlqRepository,
                objectMapper,
                new RetryPolicy(3),
                observability
        );

        UUID orderId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), orderId.toString(), envelope(orderId, correlationId, 2, orderPlacedV2Payload(orderId, correlationId)));

        consumer.pollAndProcess(10);

        var trace = traceRepository.findByCorrelationId(correlationId);
        assertThat(trace)
                .extracting(FlowTraceEntry::action)
                .contains("RECEIVED", "PROCESSED");
        assertThat(trace).allMatch(entry -> correlationId.equals(entry.correlationId()));
        assertThat(metrics.counter("events.received.total")).isEqualTo(1);
        assertThat(metrics.counter("events.processed.total")).isEqualTo(1);
    }

    @Test
    void shouldExposeConsumerLagMetric() {
        var broker = new InMemoryKafkaBroker();
        var observability = new ObservabilityService(new InMemoryFlowTraceRepository(), new MarketplaceMetrics());
        var consumer = newConsumer(broker, new InMemoryProcessedEventRepository(), new InMemoryDlqEventRepository(), observability);

        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), firstOrderId.toString(), envelope(firstOrderId, UUID.randomUUID(), 2, orderPlacedV2Payload(firstOrderId, UUID.randomUUID())));
        broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), secondOrderId.toString(), envelope(secondOrderId, UUID.randomUUID(), 2, orderPlacedV2Payload(secondOrderId, UUID.randomUUID())));

        consumer.pollAndProcess(1);

        assertThat(consumer.currentLag()).isEqualTo(1);
    }

    @Test
    void shouldCountRetriesAndDlqEventsAndKeepDlqReason() {
        var broker = new InMemoryKafkaBroker();
        var processedEvents = new InMemoryProcessedEventRepository();
        var dlqRepository = new InMemoryDlqEventRepository();
        var metrics = new MarketplaceMetrics();
        var traceRepository = new InMemoryFlowTraceRepository();
        var observability = new ObservabilityService(traceRepository, metrics);

        var consumer = newConsumer(broker, processedEvents, dlqRepository, observability);

        UUID orderId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), orderId.toString(), envelope(orderId, correlationId, 99, "{\"orderId\":\"" + orderId + "\"}"));

        consumer.pollAndProcess(10);

        var dlq = dlqRepository.findByStatus(DlqEventStatus.NEW, 10);
        assertThat(dlq).hasSize(1);
        assertThat(dlq.getFirst().reason()).contains("Unsupported OrderPlaced event version: 99");
        assertThat(metrics.counter("consumer.retries.total.payment-consumer")).isEqualTo(2);
        assertThat(metrics.counter("dlq.events.total")).isEqualTo(1);
        assertThat(traceRepository.findByCorrelationId(correlationId))
                .extracting(FlowTraceEntry::action)
                .contains("RECEIVED", "RETRY", "SENT_TO_DLQ");
    }

    @Test
    void shouldRecordDuplicateSkippedMetric() {
        var broker = new InMemoryKafkaBroker();
        var processedEvents = new InMemoryProcessedEventRepository();
        var dlqRepository = new InMemoryDlqEventRepository();
        var metrics = new MarketplaceMetrics();
        var observability = new ObservabilityService(new InMemoryFlowTraceRepository(), metrics);
        var consumer = newConsumer(broker, processedEvents, dlqRepository, observability);

        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), orderId.toString(), envelope(eventId, orderId, correlationId, 2, orderPlacedV2Payload(orderId, correlationId)));
        processedEvents.save(ProcessedEvent.processed(eventId, "payment-consumer"));

        consumer.pollAndProcess(10);

        assertThat(metrics.counter("events.duplicates.skipped.total")).isEqualTo(1);
        assertThat(dlqRepository.findByStatus(DlqEventStatus.NEW, 10)).isEmpty();
    }

    private KafkaConsumerWorker newConsumer(InMemoryKafkaBroker broker,
                                            InMemoryProcessedEventRepository processedEvents,
                                            InMemoryDlqEventRepository dlqRepository,
                                            ObservabilityService observability) {
        var paymentRepository = new InMemoryPaymentRepository();
        var publisher = new RecordingEventPublisher();
        PaymentGateway gateway = (orderId, amount) -> new PaymentGateway.PaymentReservationResult(true, "accepted");
        var eventBus = new ApplicationEventBus(List.of(
                new ReservePaymentOnOrderPlacedHandler(gateway, paymentRepository, publisher)
        ));
        return new KafkaConsumerWorker(
                "payment-consumer",
                KafkaTopic.ORDER_EVENTS.topicName(),
                "payment-group",
                broker,
                eventBus,
                processedEvents,
                dlqRepository,
                objectMapper,
                new RetryPolicy(2),
                observability
        );
    }

    private static IntegrationEventEnvelope envelope(UUID orderId, UUID correlationId, int version, String payload) {
        return envelope(UUID.randomUUID(), orderId, correlationId, version, payload);
    }

    private static IntegrationEventEnvelope envelope(UUID eventId, UUID orderId, UUID correlationId, int version, String payload) {
        return new IntegrationEventEnvelope(
                eventId,
                orderId,
                "Order",
                "OrderPlaced",
                version,
                payload,
                correlationId,
                null,
                Instant.now()
        );
    }

    private static String orderPlacedV2Payload(UUID orderId, UUID correlationId) {
        return """
                {
                  "eventId": "%s",
                  "orderId": "%s",
                  "customerId": "%s",
                  "total": {
                    "amount": "199.99",
                    "currency": "PLN"
                  },
                  "lines": [
                    {
                      "productId": "%s",
                      "quantity": 2,
                      "unitPrice": {
                        "amount": "99.99",
                        "currency": "PLN"
                      }
                    }
                  ],
                  "salesChannel": "WEB",
                  "occurredAt": "%s",
                  "correlationId": "%s",
                  "causationId": null
                }
                """.formatted(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), correlationId);
    }
}
