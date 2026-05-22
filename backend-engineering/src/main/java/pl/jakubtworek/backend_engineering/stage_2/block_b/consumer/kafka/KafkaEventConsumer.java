package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.ConsumedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq.DeadLetterPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq.DeadLetterReason;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.ProcessingResult;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.RetryingEventProcessor;

/**
 * Coordinates consuming, processing, DLQ publishing and offset committing.
 *
 * This class represents the central rule:
 * commit Kafka offset only after the event has been safely handled.
 */
public class KafkaEventConsumer<T extends ConsumedEvent> {

    private final RetryingEventProcessor<T> retryingEventProcessor;
    private final DeadLetterPublisher<T> deadLetterPublisher;
    private final OffsetCommitter offsetCommitter;

    public KafkaEventConsumer(
            RetryingEventProcessor<T> retryingEventProcessor,
            DeadLetterPublisher<T> deadLetterPublisher,
            OffsetCommitter offsetCommitter
    ) {
        this.retryingEventProcessor = retryingEventProcessor;
        this.deadLetterPublisher = deadLetterPublisher;
        this.offsetCommitter = offsetCommitter;
    }

    /**
     * Handles a single consumed Kafka record.
     *
     * Successful processing and duplicate skipping both allow offset commit.
     * Failed processing is sent to DLQ before offset commit.
     */
    public void consume(T event, KafkaRecordPosition position) {
        ProcessingResult result = retryingEventProcessor.processWithRetry(event);

        if (result == ProcessingResult.PROCESSED
                || result == ProcessingResult.DUPLICATE_SKIPPED) {
            offsetCommitter.commit(position);
            return;
        }

        deadLetterPublisher.publish(
                event,
                new DeadLetterReason(
                        "PROCESSING_FAILED",
                        "Event could not be processed after retries.",
                        null,
                        java.time.Instant.now()
                )
        );

        offsetCommitter.commit(position);
    }
}