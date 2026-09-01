package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq.DeadLetterPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.ProcessingResult;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka.KafkaEventConsumer;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka.KafkaRecordPosition;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka.OffsetCommitter;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.RetryPolicy;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.RetryingEventProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaEventConsumerTest {

    private final TestEvent event = new TestEvent(new EventMetadata(
            UUID.randomUUID(), Instant.now(), 1, "corr-1", null, "sales"
    ));
    private final KafkaRecordPosition position = new KafkaRecordPosition("orders", 0, 41);

    @Test
    void shouldRetryTransientFailureAndCommitOnlyAfterSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> operations = new ArrayList<>();
        var processor = processor(3, ignored -> {
            operations.add("attempt");
            return attempts.incrementAndGet() < 2
                    ? ProcessingResult.RETRYABLE_FAILURE
                    : ProcessingResult.PROCESSED;
        });
        KafkaEventConsumer<TestEvent> consumer = consumer(
                processor,
                (ignoredEvent, reason) -> operations.add("dlq"),
                ignoredPosition -> operations.add("commit")
        );

        consumer.consume(event, position);

        assertEquals(List.of("attempt", "attempt", "commit"), operations);
    }

    @Test
    void shouldPublishToDlqBeforeCommittingAfterRetriesAreExhausted() {
        List<String> operations = new ArrayList<>();
        KafkaEventConsumer<TestEvent> consumer = consumer(
                processor(2, ignored -> {
                    operations.add("attempt");
                    return ProcessingResult.RETRYABLE_FAILURE;
                }),
                (ignoredEvent, reason) -> operations.add("dlq"),
                ignoredPosition -> operations.add("commit")
        );

        consumer.consume(event, position);

        assertEquals(List.of("attempt", "attempt", "dlq", "commit"), operations);
    }

    @Test
    void shouldNotCommitWhenDlqPublicationFails() {
        AtomicInteger commits = new AtomicInteger();
        KafkaEventConsumer<TestEvent> consumer = consumer(
                processor(1, ignored -> ProcessingResult.NON_RETRYABLE_FAILURE),
                (ignoredEvent, reason) -> { throw new IllegalStateException("DLQ unavailable"); },
                ignoredPosition -> commits.incrementAndGet()
        );

        assertThrows(IllegalStateException.class, () -> consumer.consume(event, position));
        assertEquals(0, commits.get());
    }

    @Test
    void shouldDistinguishExhaustedRetryFromPoisonMessageInDlq() {
        AtomicReference<pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq.DeadLetterReason>
                transientReason = new AtomicReference<>();
        AtomicReference<pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq.DeadLetterReason>
                poisonReason = new AtomicReference<>();
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);

        new KafkaEventConsumer<>(
                processor(2, ignored -> ProcessingResult.RETRYABLE_FAILURE),
                (ignored, reason) -> transientReason.set(reason),
                ignored -> { },
                clock
        ).consume(event, position);
        new KafkaEventConsumer<>(
                processor(2, ignored -> ProcessingResult.NON_RETRYABLE_FAILURE),
                (ignored, reason) -> poisonReason.set(reason),
                ignored -> { },
                clock
        ).consume(event, position);

        assertEquals("RETRIES_EXHAUSTED", transientReason.get().errorCode());
        assertEquals(2, transientReason.get().attempts());
        assertEquals(clock.instant(), transientReason.get().failedAt());
        assertEquals("POISON_MESSAGE", poisonReason.get().errorCode());
        assertEquals(1, poisonReason.get().attempts());
    }

    private RetryingEventProcessor<TestEvent> processor(
            int attempts,
            pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.SingleAttemptProcessor<TestEvent> action
    ) {
        return new RetryingEventProcessor<>(
                new RetryPolicy(attempts, ignored -> Duration.ZERO),
                action
        );
    }

    private KafkaEventConsumer<TestEvent> consumer(
            RetryingEventProcessor<TestEvent> processor,
            DeadLetterPublisher<TestEvent> dlq,
            OffsetCommitter committer
    ) {
        return new KafkaEventConsumer<>(processor, dlq, committer);
    }

    private record TestEvent(EventMetadata metadata) implements ConsumedEvent {
        @Override public String aggregateId() { return "O-1"; }
        @Override public String eventType() { return "TestEvent"; }
    }
}
