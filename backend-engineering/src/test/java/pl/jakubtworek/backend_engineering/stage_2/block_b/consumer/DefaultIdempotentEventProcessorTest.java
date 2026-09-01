package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.deduplication.ProcessedEventRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.DefaultIdempotentEventProcessor;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.NonRetryableProcessingException;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.ProcessingResult;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.RetryableProcessingException;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultIdempotentEventProcessorTest {

    private final TestEvent event = new TestEvent(new EventMetadata(
            UUID.randomUUID(), Instant.now(), 1, "corr-1", null, "sales"
    ));

    @Test
    void shouldKeepMarkerOnlyAfterSuccessfulProcessing() {
        InMemoryProcessedEvents repository = new InMemoryProcessedEvents();
        AtomicInteger calls = new AtomicInteger();
        DefaultIdempotentEventProcessor<TestEvent> processor = new DefaultIdempotentEventProcessor<>(
                repository,
                ignored -> calls.incrementAndGet()
        );

        assertEquals(ProcessingResult.PROCESSED, processor.process(event));
        assertEquals(ProcessingResult.DUPLICATE_SKIPPED, processor.process(event));
        assertEquals(1, calls.get());
    }

    @Test
    void shouldRemoveMarkerAfterRetryableAndPermanentFailure() {
        InMemoryProcessedEvents retryableRepository = new InMemoryProcessedEvents();
        var retryable = new DefaultIdempotentEventProcessor<TestEvent>(
                retryableRepository,
                ignored -> { throw new RetryableProcessingException("temporary"); }
        );
        InMemoryProcessedEvents permanentRepository = new InMemoryProcessedEvents();
        var permanent = new DefaultIdempotentEventProcessor<TestEvent>(
                permanentRepository,
                ignored -> { throw new NonRetryableProcessingException("invalid payload"); }
        );

        assertEquals(ProcessingResult.RETRYABLE_FAILURE, retryable.process(event));
        assertEquals(ProcessingResult.RETRYABLE_FAILURE, retryable.process(event));
        assertEquals(ProcessingResult.NON_RETRYABLE_FAILURE, permanent.process(event));
        assertEquals(ProcessingResult.NON_RETRYABLE_FAILURE, permanent.process(event));
    }

    @Test
    void shouldNotClassifyUnknownRuntimeExceptionAsRetryable() {
        InMemoryProcessedEvents repository = new InMemoryProcessedEvents();
        var processor = new DefaultIdempotentEventProcessor<TestEvent>(
                repository,
                ignored -> { throw new NullPointerException("programming error"); }
        );

        assertEquals(ProcessingResult.NON_RETRYABLE_FAILURE, processor.process(event));
        assertEquals(ProcessingResult.NON_RETRYABLE_FAILURE, processor.process(event));
    }

    private static final class InMemoryProcessedEvents implements ProcessedEventRepository {

        private final Set<UUID> eventIds = new HashSet<>();

        @Override
        public boolean tryMarkAsProcessed(UUID eventId) {
            return eventIds.add(eventId);
        }

        @Override
        public void removeProcessedMarker(UUID eventId) {
            eventIds.remove(eventId);
        }
    }

    private record TestEvent(EventMetadata metadata) implements ConsumedEvent {
        @Override public String aggregateId() { return "O-1"; }
        @Override public String eventType() { return "TestEvent"; }
    }
}
