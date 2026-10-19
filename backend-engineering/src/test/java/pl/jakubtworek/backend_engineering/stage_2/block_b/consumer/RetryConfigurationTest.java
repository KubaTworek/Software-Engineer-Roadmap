package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.ExponentialBackoffWithJitter;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.RetryPolicy;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry.RetryingEventProcessor;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.ProcessingResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryConfigurationTest {

    @Test
    void shouldRejectInvalidRetryConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(0, ignored -> Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(1, null));
        assertThrows(IllegalArgumentException.class, () ->
                new ExponentialBackoffWithJitter(Duration.ofSeconds(2), Duration.ofSeconds(1), 2, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new ExponentialBackoffWithJitter(Duration.ZERO, Duration.ofSeconds(1), 0.5, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new ExponentialBackoffWithJitter(Duration.ZERO, Duration.ofSeconds(1), 2, 1.1));
    }

    @Test
    void shouldCalculateDeterministicCappedBackoffWithoutJitter() {
        ExponentialBackoffWithJitter strategy = new ExponentialBackoffWithJitter(
                Duration.ofMillis(100), Duration.ofMillis(250), 2, 0
        );

        assertEquals(Duration.ofMillis(100), strategy.calculateDelay(1));
        assertEquals(Duration.ofMillis(200), strategy.calculateDelay(2));
        assertEquals(Duration.ofMillis(250), strategy.calculateDelay(3));
        assertThrows(IllegalArgumentException.class, () -> strategy.calculateDelay(0));
    }

    @Test
    void shouldReportExhaustedTransientFailureAndActualBackoffSchedule() {
        List<Duration> delays = new ArrayList<>();
        var event = new TestEvent(new EventMetadata(
                UUID.randomUUID(), Instant.EPOCH, 1, "corr-1", null, "sales"));
        var processor = new RetryingEventProcessor<>(
                new RetryPolicy(3, attempt -> Duration.ofMillis(attempt * 100L)),
                ignored -> ProcessingResult.RETRYABLE_FAILURE,
                delays::add
        );

        var outcome = processor.processWithReport(event);

        assertEquals(ProcessingResult.RETRIES_EXHAUSTED, outcome.result());
        assertEquals(3, outcome.attempts());
        assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(200)), delays);
    }

    private record TestEvent(EventMetadata metadata) implements ConsumedEvent {
        @Override public String aggregateId() { return "O-1"; }
        @Override public String eventType() { return "TestEvent"; }
    }
}
