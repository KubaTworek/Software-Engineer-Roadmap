package pl.jakubtworek.cloudarchitecture.service;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.cloudarchitecture.entity.OutboxEventEntity;
import pl.jakubtworek.cloudarchitecture.repository.OutboxEventRepository;
import pl.jakubtworek.cloudarchitecture.service.OrderOutboxPublisher;
import pl.jakubtworek.cloudarchitecture.service.PubSubPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderOutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void marksSuccessfullyPublishedEvent() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        PubSubPublisher publisher = mock(PubSubPublisher.class);
        OutboxEventEntity event = event(42L);
        when(repository.lockNextPublishableBatch(NOW)).thenReturn(List.of(event));

        new OrderOutboxPublisher(repository, publisher, Clock.fixed(NOW, ZoneOffset.UTC))
                .publishPending();

        verify(publisher).publishOrderCreated(42L);
        verify(repository).save(event);
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    void keepsFailedEventPendingForANextRetry() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        PubSubPublisher publisher = mock(PubSubPublisher.class);
        OutboxEventEntity event = event(42L);
        when(repository.lockNextPublishableBatch(NOW)).thenReturn(List.of(event));
        doThrow(new RuntimeException("Pub/Sub unavailable"))
                .when(publisher).publishOrderCreated(42L);

        new OrderOutboxPublisher(repository, publisher, Clock.fixed(NOW, ZoneOffset.UTC))
                .publishPending();

        verify(repository).save(event);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void movesAnEventToDeadStateAfterTheRetryBudgetIsExhausted() {
        OutboxEventEntity event = event(42L);

        for (int attempt = 0; attempt < 10; attempt++) {
            event.recordFailedAttempt(NOW.plusSeconds(attempt), 10);
        }

        assertThat(event.getDeadAt()).isEqualTo(NOW.plusSeconds(9));
        assertThat(event.getAttempts()).isEqualTo(10);
    }

    private static OutboxEventEntity event(Long orderId) {
        return new OutboxEventEntity(
                "ORDER",
                orderId,
                "ORDER_CREATED",
                "{\"orderId\":" + orderId + "}",
                NOW
        );
    }
}
