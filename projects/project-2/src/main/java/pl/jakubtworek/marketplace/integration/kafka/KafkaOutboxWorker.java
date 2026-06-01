package pl.jakubtworek.marketplace.integration.kafka;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEvent;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;

import java.util.UUID;

/**
 * Stage 4 worker: moves durable outbox rows to Kafka topics.
 *
 * It replaces the Stage 3 in-memory outbox worker in the real asynchronous flow.
 * The old worker is intentionally kept for comparison and local exercises.
 */
@Component
public class KafkaOutboxWorker {
    private static final int DEFAULT_BATCH_SIZE = 50;

    private final OutboxEventRepository repository;
    private final KafkaMessagePublisher publisher;
    private final KafkaTopicResolver topicResolver;
    private final KafkaEnvelopeMapper envelopeMapper;

    public KafkaOutboxWorker(OutboxEventRepository repository, KafkaMessagePublisher publisher) {
        this(repository, publisher, new KafkaTopicResolver(), new KafkaEnvelopeMapper());
    }

    public KafkaOutboxWorker(OutboxEventRepository repository, KafkaMessagePublisher publisher,
                             KafkaTopicResolver topicResolver, KafkaEnvelopeMapper envelopeMapper) {
        this.repository = repository;
        this.publisher = publisher;
        this.topicResolver = topicResolver;
        this.envelopeMapper = envelopeMapper;
    }

    @Scheduled(fixedDelayString = "${marketplace.kafka-outbox.worker-delay-ms:5000}")
    public void scheduledPublish() {
        publishNew(DEFAULT_BATCH_SIZE);
    }

    @Transactional
    public int publishNew(int limit) {
        var events = repository.findNew(limit);
        events.forEach(this::publishOneSafely);
        return events.size();
    }

    @Transactional
    public int retryFailed(int limit) {
        var events = repository.findFailed(limit);
        events.forEach(this::publishOneSafely);
        return events.size();
    }

    @Transactional
    public void retryManually(UUID outboxEventId) {
        repository.markNewForRetry(outboxEventId);
        publishById(outboxEventId);
    }

    @Transactional
    public void publishById(UUID outboxEventId) {
        var event = repository.findById(outboxEventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + outboxEventId));
        if (event.status() == OutboxEventStatus.PUBLISHED) return;
        publishOneSafely(event);
    }

    public int publishUntilIdle(int batchSize, int maxIterations) {
        int total = 0;
        for (int i = 0; i < maxIterations; i++) {
            int published = publishNew(batchSize);
            total += published;
            if (published == 0) return total;
        }
        throw new IllegalStateException("Outbox is still producing new events after " + maxIterations + " iterations");
    }

    private void publishOneSafely(OutboxEvent event) {
        try {
            var topic = topicResolver.resolve(event.eventType()).topicName();
            publisher.publish(topic, event.aggregateId().toString(), envelopeMapper.toEnvelope(event));
            repository.markPublished(event.id());
        } catch (Exception e) {
            repository.markFailed(event.id(), e.getMessage());
        }
    }
}
