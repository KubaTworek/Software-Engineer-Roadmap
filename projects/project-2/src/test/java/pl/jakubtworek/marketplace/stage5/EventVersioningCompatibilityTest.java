package pl.jakubtworek.marketplace.stage5;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.integration.contracts.OrderPlacedContractMapper;
import pl.jakubtworek.marketplace.integration.contracts.UnsupportedEventVersionException;
import pl.jakubtworek.marketplace.integration.kafka.*;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.InMemoryDlqEventRepository;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.InMemoryKafkaBroker;
import pl.jakubtworek.marketplace.integration.kafka.infrastructure.InMemoryProcessedEventRepository;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.payment.application.PaymentGateway;
import pl.jakubtworek.marketplace.payment.application.ReservePaymentOnOrderPlacedHandler;
import pl.jakubtworek.marketplace.payment.domain.PaymentStatus;
import pl.jakubtworek.marketplace.payment.infrastructure.InMemoryPaymentRepository;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.testsupport.RecordingEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EventVersioningCompatibilityTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderPlacedContractMapper mapper = new OrderPlacedContractMapper(objectMapper);

    @Test
    void shouldDeserializeOrderPlacedV1AndNormalizeItToCurrentDomainEvent() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String payload = orderPlacedV1Payload(orderId, productId);

        OrderPlaced event = mapper.toDomainEvent(payload, 1);

        assertThat(event.eventType()).isEqualTo("OrderPlaced");
        assertThat(event.eventVersion()).isEqualTo(2); // internal normalized representation is current domain event
        assertThat(event.aggregateId()).isEqualTo(orderId);
        assertThat(event.total().amount()).isEqualByComparingTo("199.99");
        assertThat(event.total().currency().getCurrencyCode()).isEqualTo("PLN");
        assertThat(event.lines()).hasSize(1);
        assertThat(event.lines().getFirst().productId()).isEqualTo(productId);
    }

    @Test
    void shouldDeserializeOrderPlacedV2WithAddedOptionalFieldWithoutBreakingConsumer() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String payload = orderPlacedV2Payload(orderId, productId, "WEB", "field added by future producer");

        OrderPlaced event = mapper.toDomainEvent(payload, 2);

        assertThat(event.aggregateId()).isEqualTo(orderId);
        assertThat(event.total().amount()).isEqualByComparingTo("199.99");
        assertThat(event.lines()).hasSize(1);
    }

    @Test
    void kafkaConsumerShouldProcessBothOrderPlacedV1AndV2() {
        var broker = new InMemoryKafkaBroker();
        var paymentRepository = new InMemoryPaymentRepository();
        var processedEvents = new InMemoryProcessedEventRepository();
        var dlqRepository = new InMemoryDlqEventRepository();
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
                new RetryPolicy(3)
        );

        UUID orderIdV1 = UUID.randomUUID();
        UUID orderIdV2 = UUID.randomUUID();
        broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), orderIdV1.toString(), envelope(orderIdV1, 1, orderPlacedV1Payload(orderIdV1, UUID.randomUUID())));
        broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), orderIdV2.toString(), envelope(orderIdV2, 2, orderPlacedV2Payload(orderIdV2, UUID.randomUUID(), "WEB", null)));

        consumer.pollAndProcess(10);

        assertThat(paymentRepository.findByOrderId(orderIdV1)).isPresent();
        assertThat(paymentRepository.findByOrderId(orderIdV1).orElseThrow().status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(paymentRepository.findByOrderId(orderIdV2)).isPresent();
        assertThat(paymentRepository.findByOrderId(orderIdV2).orElseThrow().status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(dlqRepository.findByStatus(DlqEventStatus.NEW, 10)).isEmpty();
    }

    @Test
    void unsupportedBreakingVersionShouldGoToDlq() {
        var broker = new InMemoryKafkaBroker();
        var paymentRepository = new InMemoryPaymentRepository();
        var processedEvents = new InMemoryProcessedEventRepository();
        var dlqRepository = new InMemoryDlqEventRepository();
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
                new RetryPolicy(2)
        );

        UUID orderId = UUID.randomUUID();
        broker.publish(KafkaTopic.ORDER_EVENTS.topicName(), orderId.toString(), envelope(orderId, 3, "{\"orderId\":\"" + orderId + "\"}"));

        consumer.pollAndProcess(10);

        var dlq = dlqRepository.findByStatus(DlqEventStatus.NEW, 10);
        assertThat(dlq).hasSize(1);
        assertThat(dlq.getFirst().reason()).contains("Unsupported OrderPlaced event version: 3");
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    void directMapperShouldFailExplicitlyForUnsupportedVersion() {
        assertThatThrownBy(() -> mapper.toDomainEvent("{}", 99))
                .isInstanceOf(UnsupportedEventVersionException.class)
                .hasMessageContaining("Unsupported OrderPlaced event version: 99");
    }

    private static IntegrationEventEnvelope envelope(UUID orderId, int version, String payload) {
        return new IntegrationEventEnvelope(
                UUID.randomUUID(),
                orderId,
                "Order",
                "OrderPlaced",
                version,
                payload,
                UUID.randomUUID(),
                null,
                Instant.now()
        );
    }

    private static String orderPlacedV1Payload(UUID orderId, UUID productId) {
        return """
                {
                  "eventId": "%s",
                  "orderId": "%s",
                  "customerId": "%s",
                  "totalAmount": "199.99",
                  "currency": "PLN",
                  "lines": [
                    {
                      "productId": "%s",
                      "quantity": 2,
                      "unitPriceAmount": "99.99",
                      "currency": "PLN"
                    }
                  ],
                  "occurredAt": "%s",
                  "correlationId": "%s",
                  "causationId": null
                }
                """.formatted(UUID.randomUUID(), orderId, UUID.randomUUID(), productId, Instant.now(), UUID.randomUUID());
    }

    private static String orderPlacedV2Payload(UUID orderId, UUID productId, String salesChannel, String extraFieldValue) {
        String extra = extraFieldValue == null ? "" : ",\n  \"extraFieldFromFutureProducer\": \"" + extraFieldValue + "\"";
        return """
                {
                  "eventId": "%s",
                  "aggregateId": "%s",
                  "customerId": "%s",
                  "total": { "amount": "199.99", "currency": "PLN" },
                  "lines": [
                    {
                      "productId": "%s",
                      "quantity": 2,
                      "unitPrice": { "amount": "99.99", "currency": "PLN" }
                    }
                  ],
                  "salesChannel": "%s",
                  "occurredAt": "%s",
                  "correlationId": "%s",
                  "causationId": null%s
                }
                """.formatted(UUID.randomUUID(), orderId, UUID.randomUUID(), productId, salesChannel, Instant.now(), UUID.randomUUID(), extra);
    }
}
