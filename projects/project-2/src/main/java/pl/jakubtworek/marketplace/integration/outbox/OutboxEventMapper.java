package pl.jakubtworek.marketplace.integration.outbox;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.jakubtworek.marketplace.inventory.domain.StockReservationFailed;
import pl.jakubtworek.marketplace.inventory.domain.StockReserved;
import pl.jakubtworek.marketplace.ordering.domain.OrderCancelled;
import pl.jakubtworek.marketplace.ordering.domain.OrderConfirmed;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.payment.domain.PaymentRejected;
import pl.jakubtworek.marketplace.payment.domain.PaymentReserved;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class OutboxEventMapper {
    private final ObjectMapper objectMapper;
    private final Map<String, Class<? extends DomainEvent>> eventTypes = Map.of(
            "OrderPlaced", OrderPlaced.class,
            "OrderCancelled", OrderCancelled.class,
            "OrderConfirmed", OrderConfirmed.class,
            "PaymentReserved", PaymentReserved.class,
            "PaymentRejected", PaymentRejected.class,
            "StockReserved", StockReserved.class,
            "StockReservationFailed", StockReservationFailed.class
    );

    public OutboxEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public OutboxEvent toOutboxEvent(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            return new OutboxEvent(
                    event.eventId(),
                    event.aggregateId(),
                    aggregateType(event),
                    event.eventType(),
                    event.eventVersion(),
                    payload,
                    event.correlationId(),
                    event.causationId(),
                    OutboxEventStatus.NEW,
                    Instant.now(),
                    null,
                    0,
                    null
            );
        } catch (Exception e) {
            throw new OutboxSerializationException("Cannot serialize event " + event.eventType(), e);
        }
    }

    public DomainEvent toDomainEvent(OutboxEvent outboxEvent) {
        Class<? extends DomainEvent> targetType = eventTypes.get(outboxEvent.eventType());
        if (targetType == null) {
            throw new IllegalArgumentException("Unsupported outbox event type: " + outboxEvent.eventType());
        }
        try {
            return objectMapper.readValue(outboxEvent.payload(), targetType);
        } catch (Exception e) {
            throw new OutboxSerializationException("Cannot deserialize event " + outboxEvent.eventType(), e);
        }
    }

    private String aggregateType(DomainEvent event) {
        String className = event.getClass().getSimpleName();
        if (className.startsWith("Order")) return "Order";
        if (className.startsWith("Payment")) return "Payment";
        if (className.startsWith("Stock")) return "StockReservation";
        return "Unknown";
    }
}
