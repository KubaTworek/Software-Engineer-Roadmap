package com.example.paymentsystem.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxRelayTest {
    @Test
    void brokerFailureSchedulesRetryWithoutMarkingEventPublished() {
        OutboxEvent event = new OutboxEvent("Payment", UUID.randomUUID(), "PaymentCreated", "{}");
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        EventPublisher publisher = mock(EventPublisher.class);
        when(repository.lockPublishableBatch(any(Instant.class))).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker down")).when(publisher).publish(event);

        int published = new OutboxRelay(repository, publisher, new SimpleMeterRegistry()).publishBatch();

        assertThat(published).isZero();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void poisonEventStopsRetryingAndBecomesVisibleForManualReview() {
        OutboxEvent event = new OutboxEvent("Payment", UUID.randomUUID(), "PaymentCreated", "{}");

        for (int attempt = 0; attempt < OutboxEvent.MAX_DELIVERY_ATTEMPTS; attempt++) {
            event.failed(new IllegalStateException("schema rejected by consumer"));
        }

        assertThat(event.getAttemptCount()).isEqualTo(OutboxEvent.MAX_DELIVERY_ATTEMPTS);
        assertThat(event.isDeadLettered()).isTrue();
        assertThat(event.getPublishedAt()).isNull();
    }
}
