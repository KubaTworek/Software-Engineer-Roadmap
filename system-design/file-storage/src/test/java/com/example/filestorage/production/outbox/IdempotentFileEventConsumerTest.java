package com.example.filestorage.production.outbox;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.*;

class IdempotentFileEventConsumerTest {
    @Test
    void redeliveryDoesNotRepeatTransactionalEffect() {
        ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
        UUID eventId = UUID.randomUUID();
        when(repository.existsById(eventId)).thenReturn(false, true);
        var consumer = new IdempotentFileEventConsumer(repository,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        var record = new ConsumerRecord<String, String>("events", 0, 0, "FILE:1", "{}");
        record.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));

        consumer.consume(record);
        consumer.consume(record);

        verify(repository, times(1)).save(any(ProcessedEvent.class));
    }
}
