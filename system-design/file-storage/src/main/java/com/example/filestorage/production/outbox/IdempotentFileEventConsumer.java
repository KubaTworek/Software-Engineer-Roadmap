package com.example.filestorage.production.outbox;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

/**
 * Minimalny konsument pokazujacy inbox pattern. W realnym handlerze marker i
 * aktualizacja projekcji musza pozostac w tej samej transakcji PostgreSQL.
 */
@Component
public class IdempotentFileEventConsumer {
    static final String CONSUMER = "file-availability-projection";
    private final ProcessedEventRepository processed;
    private final Clock clock;

    public IdempotentFileEventConsumer(ProcessedEventRepository processed, Clock clock) {
        this.processed = processed;
        this.clock = clock;
    }

    @KafkaListener(topics = "${app.production.events.topic}",
            autoStartup = "${app.production.events.consumer-enabled:false}")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        var header = record.headers().lastHeader("eventId");
        if (header == null) throw new IllegalArgumentException("Event has no eventId header");
        UUID eventId = UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
        if (processed.existsById(eventId)) return;

        // Miejsce na idempotentna aktualizacje read modelu. Marker zapisany
        // przed commitem powoduje, ze redelivery po commicie nie powtarza efektu.
        processed.save(new ProcessedEvent(eventId, CONSUMER, clock.instant()));
    }
}
