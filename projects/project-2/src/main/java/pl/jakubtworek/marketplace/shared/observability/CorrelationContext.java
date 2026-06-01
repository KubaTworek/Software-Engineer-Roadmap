package pl.jakubtworek.marketplace.shared.observability;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

public final class CorrelationContext {
    public static final String CORRELATION_ID = "correlationId";
    public static final String CAUSATION_ID = "causationId";
    public static final String EVENT_ID = "eventId";
    public static final String ORDER_ID = "orderId";
    public static final String CONSUMER_NAME = "consumerName";
    public static final String TOPIC = "topic";

    private CorrelationContext() {}

    public static UUID currentOrNewCorrelationId() {
        return Optional.ofNullable(MDC.get(CORRELATION_ID))
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .orElseGet(UUID::randomUUID);
    }

    public static void put(String key, Object value) {
        if (value != null) MDC.put(key, value.toString());
    }

    public static void remove(String... keys) {
        for (String key : keys) MDC.remove(key);
    }

    public static MdcScope withEvent(UUID correlationId, UUID causationId, UUID eventId, UUID orderId, String consumerName, String topic) {
        return MdcScope.open()
                .put(CORRELATION_ID, correlationId)
                .put(CAUSATION_ID, causationId)
                .put(EVENT_ID, eventId)
                .put(ORDER_ID, orderId)
                .put(CONSUMER_NAME, consumerName)
                .put(TOPIC, topic);
    }
}
