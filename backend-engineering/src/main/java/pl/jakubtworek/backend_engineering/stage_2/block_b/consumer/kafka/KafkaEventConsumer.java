package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.ConsumedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq.DeadLetterPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq.DeadLetterReason;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.ProcessingResult;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.RetryingEventProcessor;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.RetryOutcome;

import java.time.Clock;
import java.util.Objects;

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
    private final Clock clock;

    public KafkaEventConsumer(
            RetryingEventProcessor<T> retryingEventProcessor,
            DeadLetterPublisher<T> deadLetterPublisher,
            OffsetCommitter offsetCommitter
    ) {
        this(retryingEventProcessor, deadLetterPublisher, offsetCommitter, Clock.systemUTC());
    }

    public KafkaEventConsumer(
            RetryingEventProcessor<T> retryingEventProcessor,
            DeadLetterPublisher<T> deadLetterPublisher,
            OffsetCommitter offsetCommitter,
            Clock clock
    ) {
        this.retryingEventProcessor = retryingEventProcessor;
        this.deadLetterPublisher = deadLetterPublisher;
        this.offsetCommitter = offsetCommitter;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Handles a single consumed Kafka record.
     *
     * Successful processing and duplicate skipping both allow offset commit.
     * Failed processing is sent to DLQ before offset commit.
     */
    public void consume(T event, KafkaRecordPosition position) {
        RetryOutcome outcome = retryingEventProcessor.processWithReport(event);
        ProcessingResult result = outcome.result();

        if (result == ProcessingResult.PROCESSED
                || result == ProcessingResult.DUPLICATE_SKIPPED) {
            offsetCommitter.commit(position);
            return;
        }

        String errorCode = result == ProcessingResult.RETRIES_EXHAUSTED
                ? "RETRIES_EXHAUSTED"
                : "POISON_MESSAGE";
        String message = result == ProcessingResult.RETRIES_EXHAUSTED
                ? "Transient failure did not recover within retry budget."
                : "Retry cannot repair this event or processing failure.";

        deadLetterPublisher.publish(event, new DeadLetterReason(
                errorCode,
                message,
                null,
                outcome.attempts(),
                clock.instant()
        ));

        offsetCommitter.commit(position);
    }
}
