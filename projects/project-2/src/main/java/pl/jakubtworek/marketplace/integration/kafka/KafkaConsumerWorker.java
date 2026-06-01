package pl.jakubtworek.marketplace.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventMapper;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.UUID;

/**
 * Polling consumer used by tests and by the stage-4 architecture.
 *
 * In production, the same logic can be called from a @KafkaListener with manual AckMode.
 * This class keeps the important rules explicit: process -> mark processed -> commit offset.
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

    public KafkaConsumerWorker(String consumerName, String topic, String consumerGroup,
                               KafkaMessageBroker broker, ApplicationEventBus eventBus,
                               ProcessedEventRepository processedEvents, DlqEventRepository dlqRepository,
                               ObjectMapper objectMapper, RetryPolicy retryPolicy) {
        this.consumerName = consumerName;
        this.topic = topic;
        this.consumerGroup = consumerGroup;
        this.broker = broker;
        this.eventBus = eventBus;
        this.processedEvents = processedEvents;
        this.dlqRepository = dlqRepository;
        this.outboxEventMapper = new OutboxEventMapper(objectMapper);
        this.retryPolicy = retryPolicy;
    }

    public int pollAndProcess(int maxRecords) {
        int processed = 0;
        for (KafkaRecord record : broker.poll(topic, consumerGroup, maxRecords)) {
            processRecord(record);
            processed++;
        }
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
        DomainEvent event = outboxEventMapper.toDomainEvent(envelopeMapper.toOutboxEvent(record.envelope()));
        eventBus.publish(event);
        processedEvents.save(ProcessedEvent.processed(record.envelope().eventId(), consumerName));
        throw new SimulatedConsumerCrashException("simulated crash before offset commit");
    }

    public void processRecord(KafkaRecord record) {
        UUID eventId = record.envelope().eventId();

        if (processedEvents.exists(eventId, consumerName)) {
            broker.commit(record.topic(), consumerGroup, record.offset());
            return;
        }

        int attempts = 0;
        while (true) {
            attempts++;
            try {
                DomainEvent event = outboxEventMapper.toDomainEvent(envelopeMapper.toOutboxEvent(record.envelope()));
                eventBus.publish(event);
                processedEvents.save(ProcessedEvent.processed(eventId, consumerName));
                broker.commit(record.topic(), consumerGroup, record.offset());
                return;
            } catch (Exception e) {
                if (attempts >= retryPolicy.maxAttempts()) {
                    dlqRepository.save(DlqEvent.newEvent(record.topic(), consumerGroup, record.offset(), record.envelope(), e.getMessage(), attempts));
                    broker.commit(record.topic(), consumerGroup, record.offset());
                    return;
                }
            }
        }
    }
}
