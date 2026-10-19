package com.example.newsfeed.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IdempotentEventProcessorTest {
    @Test
    void sameEventCanBeClaimedOnceByEveryIndependentProjection() {
        ProcessedKafkaEventRepository repository = mock(ProcessedKafkaEventRepository.class);
        UUID eventId = UUID.randomUUID();
        DomainEvent event = new DomainEvent(eventId, "post.created", UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), Map.of());
        when(repository.claim("fanout", eventId, "post.created")).thenReturn(1, 0);
        when(repository.claim("search-index", eventId, "post.created")).thenReturn(1);
        IdempotentEventProcessor processor = new IdempotentEventProcessor(repository);
        AtomicInteger effects = new AtomicInteger();

        assertThat(processor.processOnce("fanout", event, effects::incrementAndGet)).isTrue();
        assertThat(processor.processOnce("fanout", event, effects::incrementAndGet)).isFalse();
        assertThat(processor.processOnce("search-index", event, effects::incrementAndGet)).isTrue();
        assertThat(effects).hasValue(2);
    }
}
