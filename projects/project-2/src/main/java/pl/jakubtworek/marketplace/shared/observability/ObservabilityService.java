package pl.jakubtworek.marketplace.shared.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.kafka.DlqEvent;
import pl.jakubtworek.marketplace.integration.kafka.KafkaRecord;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.UUID;

@Component
public class ObservabilityService {
    private static final Logger log = LoggerFactory.getLogger(ObservabilityService.class);

    private final FlowTraceRepository flowTraceRepository;
    private final MarketplaceMetrics metrics;

    public ObservabilityService(FlowTraceRepository flowTraceRepository, MarketplaceMetrics metrics) {
        this.flowTraceRepository = flowTraceRepository;
        this.metrics = metrics;
    }

    public void eventReceived(KafkaRecord record, String consumerName) {
        var envelope = record.envelope();
        try (var ignored = CorrelationContext.withEvent(envelope.correlationId(), envelope.causationId(), envelope.eventId(), envelope.aggregateId(), consumerName, record.topic())) {
            log.info("event received from topic={} offset={} type={} version={} consumer={}", record.topic(), record.offset(), envelope.eventType(), envelope.eventVersion(), consumerName);
        }
        flowTraceRepository.append(FlowTraceEntry.of(envelope.correlationId(), envelope.eventId(), envelope.aggregateId(), "kafka-consumer", "RECEIVED", record.topic(), consumerName, envelope.eventType()));
        metrics.increment("events.received.total");
    }

    public void eventProcessed(DomainEvent event, String consumerName, String topic, long durationMillis) {
        try (var ignored = CorrelationContext.withEvent(event.correlationId(), event.causationId(), event.eventId(), event.aggregateId(), consumerName, topic)) {
            log.info("event processed type={} version={} durationMs={} consumer={}", event.eventType(), event.eventVersion(), durationMillis, consumerName);
        }
        flowTraceRepository.append(FlowTraceEntry.of(event.correlationId(), event.eventId(), event.aggregateId(), "kafka-consumer", "PROCESSED", topic, consumerName, event.eventType()));
        metrics.increment("events.processed.total");
        metrics.gauge("event.processing.duration.last.ms." + consumerName, durationMillis);
    }

    public void duplicateSkipped(KafkaRecord record, String consumerName) {
        var envelope = record.envelope();
        try (var ignored = CorrelationContext.withEvent(envelope.correlationId(), envelope.causationId(), envelope.eventId(), envelope.aggregateId(), consumerName, record.topic())) {
            log.info("duplicate event skipped type={} consumer={}", envelope.eventType(), consumerName);
        }
        flowTraceRepository.append(FlowTraceEntry.of(envelope.correlationId(), envelope.eventId(), envelope.aggregateId(), "kafka-consumer", "DUPLICATE_SKIPPED", record.topic(), consumerName, envelope.eventType()));
        metrics.increment("events.duplicates.skipped.total");
    }

    public void retryScheduled(KafkaRecord record, String consumerName, int attempt, Exception exception) {
        var envelope = record.envelope();
        try (var ignored = CorrelationContext.withEvent(envelope.correlationId(), envelope.causationId(), envelope.eventId(), envelope.aggregateId(), consumerName, record.topic())) {
            log.warn("event processing retry attempt={} type={} reason={}", attempt, envelope.eventType(), exception.getMessage());
        }
        flowTraceRepository.append(FlowTraceEntry.of(envelope.correlationId(), envelope.eventId(), envelope.aggregateId(), "kafka-consumer", "RETRY", record.topic(), consumerName, exception.getMessage()));
        metrics.increment("consumer.retries.total." + consumerName);
        metrics.increment("consumer.retries.total");
    }

    public void sentToDlq(DlqEvent event) {
        var envelope = event.envelope();
        try (var ignored = CorrelationContext.withEvent(envelope.correlationId(), envelope.causationId(), envelope.eventId(), envelope.aggregateId(), event.consumerGroup(), event.originalTopic())) {
            log.error("event sent to DLQ type={} reason={} attempts={}", envelope.eventType(), event.reason(), event.attempts());
        }
        flowTraceRepository.append(FlowTraceEntry.of(envelope.correlationId(), envelope.eventId(), envelope.aggregateId(), "dlq", "SENT_TO_DLQ", event.originalTopic(), event.consumerGroup(), event.reason()));
        metrics.increment("dlq.events.total");
    }

    public void lag(String topic, String consumerGroup, long lag) {
        metrics.gauge("consumer.lag." + topic + "." + consumerGroup, lag);
    }

    public void businessEvent(DomainEvent event, String component, String action) {
        flowTraceRepository.append(FlowTraceEntry.of(event.correlationId(), event.eventId(), event.aggregateId(), component, action, null, null, event.eventType()));
    }

    public void businessMarker(UUID correlationId, UUID orderId, String component, String action, String message) {
        flowTraceRepository.append(FlowTraceEntry.of(correlationId, null, orderId, component, action, null, null, message));
    }
}
