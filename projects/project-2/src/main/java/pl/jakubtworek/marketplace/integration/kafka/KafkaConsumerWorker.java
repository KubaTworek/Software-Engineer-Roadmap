package pl.jakubtworek.marketplace.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventMapper;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;
import pl.jakubtworek.marketplace.shared.observability.FlowTraceRepository;
import pl.jakubtworek.marketplace.shared.observability.InMemoryFlowTraceRepository;
import pl.jakubtworek.marketplace.shared.observability.MarketplaceMetrics;
import pl.jakubtworek.marketplace.shared.observability.ObservabilityService;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Polling consumer used by tests and by the stage-4/6 architecture.
 *
 * Production mapping: the same processRecord method can be called from a @KafkaListener
 * with manual AckMode. The business rule remains explicit:
 * receive -> process -> mark processed -> commit offset -> update observability.
 */
public class KafkaConsumerWorker {
    private final String consumerName;
    private final String topic;
    private final String consumerGroup;
    private final KafkaMessageBroker broker;
    private final ApplicationEventBus eventBus;
    private final ProcessedEventRepository processedEvents;
    private final DlqEventRepository dlqRepository;
    private final KafkaEnvelopeMapper envelopeMapper = new KafkaEnvelopeMapper();
    private final OutboxEventMapper outboxEventMapper;
    private final RetryPolicy retryPolicy;
    private final ObservabilityService observability;

    public KafkaConsumerWorker(String consumerName, String topic, String consumerGroup,
                               KafkaMessageBroker broker, ApplicationEventBus eventBus,
                               ProcessedEventRepository processedEvents, DlqEventRepository dlqRepository,
                               ObjectMapper objectMapper, RetryPolicy retryPolicy) {
        this(consumerName, topic, consumerGroup, broker, eventBus, processedEvents, dlqRepository,
                objectMapper, retryPolicy, new ObservabilityService(new InMemoryFlowTraceRepository(), new MarketplaceMetrics()));
    }

    public KafkaConsumerWorker(String consumerName, String topic, String consumerGroup,
                               KafkaMessageBroker broker, ApplicationEventBus eventBus,
                               ProcessedEventRepository processedEvents, DlqEventRepository dlqRepository,
                               ObjectMapper objectMapper, RetryPolicy retryPolicy,
                               ObservabilityService observability) {
        this.consumerName = consumerName;
        this.topic = topic;
        this.consumerGroup = consumerGroup;
        this.broker = broker;
        this.eventBus = eventBus;
        this.processedEvents = processedEvents;
        this.dlqRepository = dlqRepository;
        this.outboxEventMapper = new OutboxEventMapper(objectMapper);
        this.retryPolicy = retryPolicy;
        this.observability = observability;
    }

    public int pollAndProcess(int maxRecords) {
        int processed = 0;
        for (KafkaRecord record : broker.poll(topic, consumerGroup, maxRecords)) {
            processRecord(record);
            processed++;
        }
        recordLag();
        return processed;
    }

    /**
     * Test helper for the classic failure mode: business side effect succeeded and
     * processed_events was written, but the process crashed before committing the offset.
     */
    public void simulateCrashAfterSuccessfulProcessingBeforeCommit(KafkaRecord record) {
        if (processedEvents.exists(record.envelope().eventId(), consumerName)) {
            throw new IllegalStateException("event was already processed before crash simulation");
        }
        observability.eventReceived(record, consumerName);
        DomainEvent event = outboxEventMapper.toDomainEvent(envelopeMapper.toOutboxEvent(record.envelope()));
        eventBus.publish(event);
        processedEvents.save(ProcessedEvent.processed(record.envelope().eventId(), consumerName));
        observability.eventProcessed(event, consumerName, record.topic(), 0);
        throw new SimulatedConsumerCrashException("simulated crash before offset commit");
    }

    public void processRecord(KafkaRecord record) {
        UUID eventId = record.envelope().eventId();
        observability.eventReceived(record, consumerName);

        if (processedEvents.exists(eventId, consumerName)) {
            broker.commit(record.topic(), consumerGroup, record.offset());
            observability.duplicateSkipped(record, consumerName);
            recordLag();
            return;
        }

        int attempts = 0;
        while (true) {
            attempts++;
            Instant startedAt = Instant.now();
            try {
                DomainEvent event = outboxEventMapper.toDomainEvent(envelopeMapper.toOutboxEvent(record.envelope()));
                eventBus.publish(event);
                processedEvents.save(ProcessedEvent.processed(eventId, consumerName));
                broker.commit(record.topic(), consumerGroup, record.offset());
                observability.eventProcessed(event, consumerName, record.topic(), Duration.between(startedAt, Instant.now()).toMillis());
                recordLag();
                return;
            } catch (Exception e) {
                observability.retryScheduled(record, consumerName, attempts, e);
                if (attempts >= retryPolicy.maxAttempts()) {
                    DlqEvent dlqEvent = DlqEvent.newEvent(record.topic(), consumerGroup, record.offset(), record.envelope(), e.getMessage(), attempts);
                    dlqRepository.save(dlqEvent);
                    observability.sentToDlq(dlqEvent);
                    broker.commit(record.topic(), consumerGroup, record.offset());
                    recordLag();
                    return;
                }
            }
        }
    }

    public long currentLag() {
        long endOffset = broker.endOffset(topic);
        long committedOffset = broker.committedOffset(topic, consumerGroup);
        return Math.max(0, endOffset - committedOffset);
    }

    private void recordLag() {
        observability.lag(topic, consumerGroup, currentLag());
    }
}
